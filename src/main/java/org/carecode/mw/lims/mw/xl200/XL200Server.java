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
import java.util.function.Consumer;

public class XL200Server {

    private static final Logger logger = LogManager.getLogger(XL200Server.class);
    private static final int PORT = XL200SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerPort();

    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char ENQ = 0x05;
    private static final char ACK = 0x06;
    private static final char NAK = 0x15;
    private static final char EOT = 0x04;

    private ServerSocket serverSocket;

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            logger.info("Server started on port {}", PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Accepted connection from {}:{}", clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());
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
        final String[] lastFrameSent = new String[1];

        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            logger.info("Session started with {}:{}", socket.getInetAddress().getHostAddress(), socket.getPort());

            StringBuilder frame = new StringBuilder();
            int b;

            while ((b = in.read()) != -1) {
                if (b == ENQ) {
                    out.write(ACK);
                    out.flush();
                    logger.debug("Received ENQ, sent ACK");
                } else if (b == ACK) {
                    logger.debug("Received ACK from analyzer");
                } else if (b == NAK) {
                    logger.warn("Received NAK from analyzer");
                    if (lastFrameSent[0] != null) {
                        out.write(lastFrameSent[0].getBytes());
                        out.flush();
                        logger.info("Retransmitted last frame due to NAK");
                    }
                } else if (b == STX) {
                    frame.setLength(0);
                    logger.debug("Start of new ASTM frame");
                } else if (b == ETX) {
                    String message = frame.toString();
                    logger.debug("Received ASTM Frame: {}", message);
                    List<String> records = splitRecords(message);
                    logger.debug("records: {}", records);
                    DataBundle db = processRecords(records, out, frameStr -> lastFrameSent[0] = frameStr);
                    resultCount = db.getResultsRecords().size();
                    if (db.getPatientRecord() != null) {
                        sampleIds.add(db.getPatientRecord().getPatientId());
                    }
                    out.write(ACK);
                    out.flush();
                    logger.debug("Sent ACK for ASTM frame");

                    // Reset frame buffer and skip checksum and CR/LF
                    frame.setLength(0);
                    for (int i = 0; i < 4; i++) {
                        in.read();
                    }
                } else if (b == EOT) {
                    logger.debug("Received EOT.");
                    frame.setLength(0);
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
            logger.info("Connection with {}:{} closed", socket.getInetAddress().getHostAddress(), socket.getPort());
            Instant sessionEnd = Instant.now();
            long seconds = Duration.between(sessionStart, sessionEnd).getSeconds();
            logger.info("Session complete. Duration: {}s, Results: {}, Samples: {}", seconds, resultCount, sampleIds);
        }
    }

    private List<String> splitRecords(String msg) {
        logger.debug("splitRecords");
        msg = msg.replace("\r", "\n").replaceAll("\n+", "\n");
        return Arrays.asList(msg.split("\n"));
    }

    private DataBundle processRecords(List<String> records, OutputStream out, java.util.function.Consumer<String> sentCallback) {
        logger.debug("processRecords");
        logger.debug("processRecords.records {}", records);
        DataBundle db = new DataBundle();
        db.setMiddlewareSettings(XL200SettingsLoader.getSettings());

        String currentSampleId = null;

        for (String rec : records) {
            logger.debug("Processing record: {}", rec);
            if (rec.startsWith("P|")) {
                PatientRecord pr = XL200Parsers.parsePatientRecord(rec);
                db.setPatientRecord(pr);
                logger.debug("P {}", pr);
            } else if (rec.startsWith("Q|")) {
                logger.debug("Q {}", rec);
                QueryRecord qr = XL200Parsers.parseQueryRecord(rec);
                logger.debug("qr {}", qr);
                db.getQueryRecords().add(qr);
                currentSampleId = qr.getSampleId();
                logger.debug("currentSampleId{}", currentSampleId);
                DataBundle orders = XL200LISCommunicator.pullTestOrdersForSampleRequests(qr);
                logger.debug("Q {}", orders);
                if (orders != null && !orders.getOrderRecords().isEmpty()) {
                    logger.debug("Received order bundle: {}", orders);
                    try {
                        XL200LISCommunicator.sendAstmResponseBlock(orders, out, sentCallback);
                    } catch (IOException e) {
                        logger.error("Failed to send ASTM response block", e);
                    }
                } else {
                    logger.debug("No order bundle returned for sample {}", qr.getSampleId());
                }
            } else if (rec.startsWith("O|")) {
                OrderRecord or = XL200Parsers.parseOrderRecord(rec);
                db.getOrderRecords().add(or);
                currentSampleId = or.getSampleId();
            } else if (rec.startsWith("R|")) {
                ResultsRecord rr = XL200Parsers.parseResultsRecord(rec);
                rr.setSampleId(currentSampleId);
                db.getResultsRecords().add(rr);
            }
        }

        if (!db.getResultsRecords().isEmpty()) {
            logger.debug("Pushing {} result records to LIMS", db.getResultsRecords().size());
            boolean success = XL200LISCommunicator.pushResults(db);
            if (success) {
                logger.debug("Results pushed successfully");
            } else if (!XL200LISCommunicator.isIgnoreLimsResponse()) {
                logger.warn("Failed to push results to LIMS");
            }
        }
        return db;
    }
}
