package org.carecode.mw.lims.mw.xl200;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class XL200Server {

    private static final Logger logger = LogManager.getLogger(XL200Server.class);
    private static final int PORT = XL200SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerPort();

    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char ENQ = 0x05;
    private static final char ACK = 0x06;
    private static final char EOT = 0x04;

    private ServerSocket serverSocket;

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            logger.info("Server started on port {}", PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            logger.error("Failed to start server on port {}", PORT, e);
        }
    }

    private void handleClient(Socket socket) {
        Instant sessionStart = Instant.now();
        List<String> sampleIds = new ArrayList<>();
        int resultCount = 0;

        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream()); BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            StringBuilder frame = new StringBuilder();
            int b;

            while ((b = in.read()) != -1) {
                if (b == ENQ) {
                    out.write(ACK);
                    out.flush();
                    logger.debug("Received ENQ, sent ACK");
                } else if (b == STX) {
                    frame.setLength(0);
                } else if (b == ETX) {
                    String message = frame.toString();
                    logger.debug("Received ASTM Frame: {}", message);
                    List<String> records = splitRecords(message);
                    DataBundle db = processRecords(records);
                    resultCount = db.getResultsRecords().size();
                    if (db.getPatientRecord() != null) {
                        sampleIds.add(db.getPatientRecord().getPatientId());
                    }
                    out.write(ACK);
                    out.flush();

                    // Discard checksum (2 bytes) and trailing CR/LF after ETX
                    for (int i = 0; i < 4; i++) {
                        if (in.read() == -1) {
                            break;
                        }
                    }
                    // Reset for next transmission
                    frame.setLength(0);
                } else if (b == EOT) {
                    logger.debug("Received EOT.");
                    if (frame.length() > 0) {
                        String message = frame.toString();
                        logger.debug("Received ASTM Frame: {}", message);
                        List<String> records = splitRecords(message);
                        DataBundle db = processRecords(records);
                        resultCount = db.getResultsRecords().size();
                        if (db.getPatientRecord() != null) {
                            sampleIds.add(db.getPatientRecord().getPatientId());
                        }
                        out.write(ACK);
                        out.flush();
                    }
                    frame.setLength(0);
                    // continue waiting for next transmission
                } else {
                    frame.append((char) b);
                }
            }

        } catch (IOException e) {
            logger.error("Client communication error", e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignore) {
            }
            Instant sessionEnd = Instant.now();
            long seconds = Duration.between(sessionStart, sessionEnd).getSeconds();
            logger.info("Session complete. Duration: {}s, Results: {}, Samples: {}", seconds, resultCount, sampleIds);
        }
    }

    private List<String> splitRecords(String msg) {
        msg = msg.replace("\r", "\n").replaceAll("\n+", "\n");
        return Arrays.asList(msg.split("\n"));
    }

    private DataBundle processRecords(List<String> records) {
        DataBundle db = new DataBundle();
        db.setMiddlewareSettings(XL200SettingsLoader.getSettings());

        String currentSampleId = null;

        for (String rec : records) {
            if (rec.startsWith("P|")) {
                PatientRecord pr = XL200Parsers.parsePatientRecord(rec);
                db.setPatientRecord(pr);
            } else if (rec.startsWith("Q|")) {
                QueryRecord qr = XL200Parsers.parseQueryRecord(rec);
                db.getQueryRecords().add(qr);
                currentSampleId = qr.getSampleId();
                logger.debug("Query for sample ID: {}", currentSampleId);
                String endpoint = XL200SettingsLoader.getSettings().getLimsSettings()
                    .getLimsServerBaseUrl() + "/test_orders_for_sample_requests";
                logger.debug("Pulling test orders for sample {} from {}", currentSampleId, endpoint);
                // Forward query record to the LIMS to fetch any pending test orders
                XL200LISCommunicator.pullTestOrdersForSampleRequests(qr);
            } else if (rec.startsWith("R|")) {
                ResultsRecord rr = XL200Parsers.parseResultsRecord(rec);
                rr.setSampleId(currentSampleId);
                db.getResultsRecords().add(rr);
            }
        }

        if (!db.getResultsRecords().isEmpty()) {
            boolean success = XL200LISCommunicator.pushResults(db);
            if (!success && !XL200LISCommunicator.isIgnoreLimsResponse()) {
                logger.warn("Failed to push results to LIMS");
            }
        }
        return db;
    }
}
