package org.carecode.mw.lims.mw.xl200;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class XL200LISCommunicator {

    private static final Logger logger = LogManager.getLogger(XL200LISCommunicator.class);
    private static final Gson gson = new Gson();
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char CR = 0x0D;
    private static final char LF = 0x0A;
    private static final char EOT = 0x04;
    private static final char ACK = 0x06;
    private static final char NAK = 0x15;

    public static DataBundle pullTestOrdersForSampleRequests(QueryRecord queryRecord) {
        logger.info("pullTestOrdersForSampleRequests");
        try {
            String baseUrl = XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            logger.info("Requesting test orders for sample {} from {}", queryRecord.getSampleId(), baseUrl);

            URL url = new URL(baseUrl + "/test_orders_for_sample_requests");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            DataBundle databundle = new DataBundle();
            databundle.setMiddlewareSettings(XL200SettingsLoader.getSettings());
            databundle.getQueryRecords().add(queryRecord);

            String jsonInputString = gson.toJson(databundle);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            logger.debug("LIMS response code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                logger.debug("LIMS response body: {}", response);
                return gson.fromJson(response.toString(), DataBundle.class);
            }
        } catch (Exception e) {
            logger.error("Error in pullTestOrdersForSampleRequests", e);
        }

        return null;
    }

    public static boolean pushResults(DataBundle patientDataBundle) {
        try {
            String pushResultsEndpoint = XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl() + "/test_results";
            URL url = new URL(pushResultsEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            patientDataBundle.setMiddlewareSettings(XL200SettingsLoader.getSettings());
            String jsonInputString = gson.toJson(patientDataBundle);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            logger.debug("Push result response code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                JsonObject responseObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                logger.info("Response from server: {}", responseObject);
                return true;
            }
        } catch (Exception e) {
            logger.error("Exception in pushResults", e);
        }
        return false;
    }

    public static void sendAstmResponseBlock(DataBundle bundle, InputStream in, OutputStream out, java.util.function.Consumer<String> sentCallback) throws IOException {
        logger.info("Sending ASTM response for sample {}", bundle.getPatientRecord().getPatientId());

        List<String> records = new ArrayList<>();

        // Generate current timestamp for Header Field 14
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String currentTimestamp = sdf.format(new Date());

        // Header: H|`^&|F3|F4|F5|F6|F7|F8|F9|F10|F11|F12|F13|F14
        // Field 2: `^& (correct delimiters with backtick)
        // Field 5: CareCode LIMS (sender name)
        // Field 12: P (Processing ID - Production)
        // Field 13: E 1394-97 (ASTM version - note the space, not hyphen)
        // Field 14: Current timestamp (YYYYMMDDHHMMSS)
        records.add("H|`^&|||CareCode LIMS|||||||P|E 1394-97|" + currentTimestamp);
        if (bundle.getPatientRecord() != null) {
            PatientRecord p = bundle.getPatientRecord();
            records.add("P|1|" + p.getPatientId() + "||" + p.getAdditionalId() + "|" + p.getPatientName() + "|" + p.getPatientSex());
        }

        for (OrderRecord o : bundle.getOrderRecords()) {
            String testCodes = o.getTestNames().stream().map(t -> "^^^" + t).reduce((a, b) -> a + "\\" + b).orElse("");
            String orderDate = o.getOrderDateTimeStr(); // YYYYMMDDHHMMSS
            String specimenCode = (o.getSpecimenCode() != null && !o.getSpecimenCode().isEmpty()) ? o.getSpecimenCode() : "S";

            // Map specimen code to full descriptor name as expected by analyzer
            String specimenDescriptor;
            switch (specimenCode.toUpperCase()) {
                case "S":
                    specimenDescriptor = "SERUM";
                    break;
                case "P":
                    specimenDescriptor = "PLASMA";
                    break;
                case "U":
                    specimenDescriptor = "URINE";
                    break;
                case "W":
                    specimenDescriptor = "WHOLE BLOOD";
                    break;
                default:
                    specimenDescriptor = specimenCode;
            }

            // IMPORTANT: When LIMS sends orders to analyzer, use sample ID WITHOUT ^01 suffix
            // The ^01 container suffix is only used BY the analyzer when it sends results back to LIMS
            // Per manual example: Q|1|^10006122 -> O|1|10006122|IPat1|... (no ^01)
            String specimenId = o.getSampleId();

            // Order Record format: O|seq|specimenID|instSpecID|testID|priority|requestedDateTime|collectionDateTime|collectionEndTime|volume|collectorID|actionCode|dangerCode|clinicalInfo|receivedDateTime|specimenDescriptor|...|reportType
            // Fields: 1|2|3|4|5|6|7|8|9|10|11|12|13|14|15|16|...|26
            // Mandatory fields for LIMS to ASTM: Priority(6)=R, OrderDate(7), ActionCode(12)=N, ReportType(26)=O
            String line = String.format("O|1|%s||%s|R|%s|||||N|||%s|%s|||||||||O", specimenId, testCodes, orderDate, orderDate, specimenDescriptor);
            records.add(line);
        }

        records.add("L|1|N");

        int frameNum = 1;
        for (String rec : records) {
            boolean frameAcknowledged = false;
            int retryCount = 0;
            final int MAX_RETRIES = 3;

            while (!frameAcknowledged && retryCount < MAX_RETRIES) {
                String framed = buildAstmFrame(frameNum, rec);
                out.write(framed.getBytes());
                out.flush();
                if (sentCallback != null) {
                    sentCallback.accept(framed);
                }
                logger.debug("Sent ASTM frame {}: {}", frameNum, rec);

                // Wait for ACK or NAK from analyzer
                long startTime = System.currentTimeMillis();
                boolean receivedResponse = false;

                while (System.currentTimeMillis() - startTime < 5000) { // 5 second timeout
                    if (in.available() > 0) {
                        int response = in.read();
                        if (response == ACK) {
                            logger.debug("Received ACK for frame {}", frameNum);
                            frameAcknowledged = true;
                            receivedResponse = true;
                            break;
                        } else if (response == NAK) {
                            logger.warn("Received NAK for frame {}, will retry", frameNum);
                            retryCount++;
                            receivedResponse = true;
                            break;
                        }
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }

                if (!receivedResponse) {
                    logger.error("Timeout waiting for ACK/NAK for frame {}", frameNum);
                    throw new IOException("Timeout waiting for acknowledgment from analyzer");
                }
            }

            if (!frameAcknowledged) {
                logger.error("Failed to send frame {} after {} retries", frameNum, MAX_RETRIES);
                throw new IOException("Failed to get acknowledgment after maximum retries");
            }

            frameNum = (frameNum + 1) % 8;
        }

        out.write(EOT);
        out.flush();
        logger.info("EOT sent to complete ASTM block.");
    }

    private static String buildAstmFrame(int frameNumber, String line) {
        String frame = "" + STX + (char) ('0' + (frameNumber % 8)) + line + ETX;
        String checksum = calculateChecksum(frame);
        return frame + checksum + CR + LF;
    }

    public static String calculateChecksum(String frame) {
        int sum = 0;
        boolean start = false;

        for (char c : frame.toCharArray()) {
            if (c == STX) {
                sum = 0;
                start = true;
                continue;
            }
            if (start) {
                sum += c;
                if (c == ETX) {
                    break;
                }
            }
        }

        String hex = Integer.toHexString(sum % 256).toUpperCase();
        return hex.length() < 2 ? "0" + hex : hex;
    }

    public static boolean isIgnoreLimsResponse() {
        return false;
    }
}
