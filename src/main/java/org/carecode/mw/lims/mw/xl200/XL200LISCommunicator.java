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

//    static boolean testing = true;
    private static final Gson gson = new Gson();

    public static DataBundle pullTestOrdersForSampleRequests(QueryRecord queryRecord) {
        logger.info("pullTestOrdersForSampleRequests");
//        if (testing) {
//            PatientDataBundle pdb = new PatientDataBundle();
//            List<String> testNames = Arrays.asList("HDL", "RF2");
//            OrderRecord or = new OrderRecord(0, queryRecord.getSampleId(), testNames, "S", new Date(), "testInformation");
//            pdb.getOrderRecords().add(or);
//            PatientRecord pr = new PatientRecord(0, "1010101", "111111", "Buddhika Ariyaratne", "M H B", "Male", "Sinhalese", null, "Galle", "0715812399", "Dr Niluka");
//            pdb.setPatientRecord(pr);
//            return pdb;
//        }

        try {
            String postSampleDataEndpoint = XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl();
            logger.debug("postSampleDataEndpoint = " + postSampleDataEndpoint);
            URL url = new URL(postSampleDataEndpoint + "/test_orders_for_sample_requests");
            logger.debug("url = " + url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            logger.debug("queryRecord = " + queryRecord);
            // Convert QueryRecord to JSON

            DataBundle databundle = new DataBundle();
            databundle.setMiddlewareSettings(XL200SettingsLoader.getSettings());
            databundle.getQueryRecords().add(queryRecord);
            String jsonInputString = gson.toJson(databundle);
            logger.debug("jsonInputString = " + jsonInputString);
            // Send the request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            logger.info("responseCode = " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("OK responseCode = " + responseCode);
                // Process response
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                logger.debug("response.toString() = " + response.toString());
                // Convert the response to a PatientDataBundle object
                DataBundle patientDataBundle = gson.fromJson(response.toString(), DataBundle.class);
                logger.debug("patientDataBundle = " + patientDataBundle);
                return patientDataBundle;
            } else {
                logger.error("POST request failed. Response code: " + responseCode);
            }
        } catch (Exception e) {
            logger.error("Error pulling test orders", e);
        }

        return null;
    }

    public static void pushResults(DataBundle patientDataBundle) {
        logger.info("pushResults = ");
        try {
            logger.debug("XL200SettingsLoader.getSettings() = " + XL200SettingsLoader.getSettings());
            logger.debug("XL200SettingsLoader.getSettings().getLimsSettings() = " + XL200SettingsLoader.getSettings().getLimsSettings());
            logger.debug("XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl() = " + XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl());
            String pushResultsEndpoint = XL200SettingsLoader.getSettings().getLimsSettings().getLimsServerBaseUrl() + "/test_results";

            
            for(ResultsRecord rr:patientDataBundle.getResultsRecords()){
                logger.debug("rr value  = " + rr.getResultValue() + "");
                logger.debug("rr value string = " + rr.getResultValueString());
            }
            
            
            URL url = new URL(pushResultsEndpoint);
            logger.debug("url = " + url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            // Serialize PatientDataBundle to JSON
            patientDataBundle.setMiddlewareSettings(XL200SettingsLoader.getSettings());
            String jsonInputString = gson.toJson(patientDataBundle);
            logger.debug("jsonInputString = " + jsonInputString);
            // Send the JSON in the request body
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            logger.info("responseCode = " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("ok");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                logger.debug("response.toString() = " + response.toString());

                // Optionally process the server response (if needed)
                JsonObject responseObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                XL200.logger.info("Response from server: " + responseObject.toString());

// Extract status
                String status = responseObject.get("status").getAsString();
                XL200.logger.info("Status: " + status);

// Extract the list of ResultsRecord objects
                Gson gson = new Gson();
                JsonArray detailsArray = responseObject.getAsJsonArray("details");

// Deserialize the JSON array into a list of ResultsRecord objects
                List<ResultsRecord> resultsRecords = new ArrayList<>();
                for (JsonElement element : detailsArray) {
                    ResultsRecord record = gson.fromJson(element, ResultsRecord.class);
                    resultsRecords.add(record);
                }

// Log and process the ResultsRecord objects as needed
                for (ResultsRecord record : resultsRecords) {
                    XL200.logger.info("Sample ID: " + record.getSampleId()
                            + ", Test: " + record.getTestCode()
                            + ", Status: " + record.getStatus());
                }

            } else {
                logger.error("POST request failed. Response code: " + responseCode);
            }
        } catch (Exception e) {
            logger.error("Error pushing results", e);
        }
    }

}
