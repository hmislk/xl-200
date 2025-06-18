package org.carecode.mw.lims.mw.xl200;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.*;

public class XL200LISCommunicator {

    private static final Logger logger = LogManager.getLogger(XL200LISCommunicator.class);
    private static final Gson gson = new Gson();

    public static DataBundle pullTestOrdersForSampleRequests(QueryRecord queryRecord) {
        logger.info("pullTestOrdersForSampleRequests");
        try {
            String baseUrl = XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            URL url = new URL(baseUrl + "/test_orders_for_sample_requests");
            logger.info("Requesting test orders for sample {} from {}", queryRecord.getSampleId(), url);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            DataBundle bundle = new DataBundle();
            bundle.setMiddlewareSettings(XL200SettingsLoader.getSettings());
            bundle.getQueryRecords().add(queryRecord);

            String json = gson.toJson(bundle);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            int code = conn.getResponseCode();
            logger.debug("LIMS response code: {}", code);
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                logger.debug("LIMS response body: {}", response);
                return gson.fromJson(response.toString(), DataBundle.class);
            } else {
                logger.error("POST request failed. Response code: {}", code);
            }
        } catch (Exception e) {
            logger.error("Exception in pullTestOrdersForSampleRequests", e);
        }
        return null;
    }

    public static boolean pushResults(DataBundle dataBundle) {
        try {
            String baseUrl = XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            URL url = new URL(baseUrl + "/test_results");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            dataBundle.setMiddlewareSettings(XL200SettingsLoader.getSettings());
            String json = gson.toJson(dataBundle);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("utf-8"));
            }

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                logger.info("Response from server: {}", jsonObject);

                String status = jsonObject.get("status").getAsString();
                logger.info("Status: {}", status);

                JsonArray details = jsonObject.getAsJsonArray("details");
                for (JsonElement el : details) {
                    ResultsRecord r = gson.fromJson(el, ResultsRecord.class);
                    logger.info("Sample ID: {}, Test: {}, Status: {}", r.getSampleId(), r.getTestCode(), r.getStatus());
                }
                return true;
            } else {
                logger.error("POST request failed. Response code: {}", code);
            }
        } catch (Exception e) {
            logger.error("Exception in pushResults", e);
        }
        return false;
    }

    public static boolean isIgnoreLimsResponse() {
        return false;
    }

    public static void sendAstmResponseBlock(DataBundle bundle, OutputStream out) throws IOException {
        logger.info("Sending ASTM response for sample {}", bundle.getPatientRecord() != null ? bundle.getPatientRecord().getPatientId() : "Unknown");

        List<String> lines = new ArrayList<>();
        int frame = 1;

        lines.add(frame++ + "H|\^&|||CareCode LIMS||||||||P");

        if (bundle.getPatientRecord() != null) {
            PatientRecord p = bundle.getPatientRecord();
            lines.add(frame++ + "P|1|" + p.getPatientId() + "||" + p.getAdditionalId() + "||"
                    + p.getPatientName() + "||" + p.getPatientSex());
        }

        for (OrderRecord o : bundle.getOrderRecords()) {
            String tests = String.join("\\", o.getTestNames());
            lines.add(frame++ + "O|1|" + o.getSampleId() + "||^^^" + tests + "|||||||" + o.getSpecimenCode());
        }

        lines.add(frame++ + "L|1|N");

        out.write(0x05); // ENQ
        out.flush();

        for (String l : lines) {
            String msg = buildAstmFrame(l);
            out.write(msg.getBytes());
            out.flush();
        }

        out.write(0x04); // EOT
        out.flush();
        logger.info("ASTM block sent.");
    }

    private static String buildAstmFrame(String line) {
        char STX = 0x02;
        char ETX = 0x03;
        char CR = 0x0D;
        char LF = 0x0A;

        String body = STX + line + CR + ETX;
        String checksum = calculateChecksum(body);
        return body + checksum + CR + LF;
    }

    private static String calculateChecksum(String frame) {
        int sum = 0;
        boolean start = false;
        for (char c : frame.toCharArray()) {
            if (c == 0x02) {
                start = true;
                continue;
            }
            if (!start) {
                continue;
            }
            sum += c;
            if (c == 0x03) {
                break;
            }
        }
        String hex = Integer.toHexString(sum % 256).toUpperCase();
        return hex.length() < 2 ? "0" + hex : hex;
    }
}
