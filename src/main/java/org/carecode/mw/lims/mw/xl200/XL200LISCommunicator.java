package org.carecode.mw.lims.mw.xl200;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.carecode.lims.libraries.DataBundle;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class XL200LISCommunicator {

    private static final Logger logger = LogManager.getLogger(XL200LISCommunicator.class);
    private static final Gson gson = new Gson();

    public static DataBundle pullTestOrdersForSampleRequests(QueryRecord queryRecord) {
        logger.info("pullTestOrdersForSampleRequests");
        try {
            String postSampleDataEndpoint = XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            URL url = new URL(postSampleDataEndpoint + "/test_orders_for_sample_requests");
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
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                return gson.fromJson(response.toString(), DataBundle.class);
            } else {
                logger.error("POST request failed. Response code: " + responseCode);
            }
        } catch (Exception e) {
            logger.error("Exception in pullTestOrdersForSampleRequests", e);
        }

        return null;
    }

    public static void pushResults(DataBundle patientDataBundle) {
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

                String status = responseObject.get("status").getAsString();
                logger.info("Status: {}", status);

                JsonArray detailsArray = responseObject.getAsJsonArray("details");
                List<ResultsRecord> resultsRecords = new ArrayList<>();
                for (JsonElement element : detailsArray) {
                    ResultsRecord record = gson.fromJson(element, ResultsRecord.class);
                    resultsRecords.add(record);
                }

                for (ResultsRecord record : resultsRecords) {
                    logger.info("Sample ID: {}, Test: {}, Status: {}",
                        record.getSampleId(), record.getTestCode(), record.getStatus());
                }
            } else {
                logger.error("POST request failed. Response code: {}", responseCode);
            }
        } catch (Exception e) {
            logger.error("Exception in pushResults", e);
        }
    }
} 
