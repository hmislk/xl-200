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

    public static void sendAstmResponseBlock(DataBundle bundle, OutputStream out) throws IOException {
        logger.info("Sending ASTM response for sample {}", bundle.getPatientRecord().getPatientId());

        List<String> records = new ArrayList<>();

        records.add("H|\\^&|||CareCode LIMS|||||||P");
        if (bundle.getPatientRecord() != null) {
            PatientRecord p = bundle.getPatientRecord();
            records.add("P|1|" + p.getPatientId() + "||" + p.getAdditionalId() + "|" + p.getPatientName() + "|" + p.getPatientSex());
        }

        for (OrderRecord o : bundle.getOrderRecords()) {
            String testCodes = o.getTestNames().stream().map(t -> "^^^" + t).reduce((a, b) -> a + "\\" + b).orElse("");
            records.add("O|1|" + o.getSampleId() + "||" + testCodes + "||||||S");
        }

        records.add("L|1|N");

        int frameNum = 1;
        for (String rec : records) {
            String framed = buildAstmFrame(frameNum, rec);
            out.write(framed.getBytes());
            out.flush();
            logger.debug("Sent ASTM line: {}", rec);

            frameNum = frameNum % 7 + 1; // cycle 1..7

            try {
                Thread.sleep(200); // slight delay to avoid analyzer overflow
            } catch (InterruptedException ignored) {
            }
        }

        out.write(EOT);
        out.flush();
        logger.info("EOT sent to complete ASTM block.");
    }

    private static String buildAstmFrame(int frameNumber, String line) {
        StringBuilder sb = new StringBuilder();
        sb.append(STX);
        sb.append((char) ('0' + (frameNumber % 8)));
        sb.append(line);
        sb.append(CR);
        sb.append(ETX);
        String checksum = calculateChecksum(sb.toString());
        sb.append(checksum);
        sb.append(CR).append(LF);
        return sb.toString();
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
                if (c == ETX) break;
            }
        }

        String hex = Integer.toHexString(sum % 256).toUpperCase();
        return hex.length() < 2 ? "0" + hex : hex;
    }

    public static boolean isIgnoreLimsResponse() {
        return false;
    }
}
