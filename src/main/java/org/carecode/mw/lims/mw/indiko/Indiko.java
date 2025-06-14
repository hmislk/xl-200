package org.carecode.mw.lims.mw.indiko;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.DataBundle;
import org.carecode.lims.libraries.PatientRecord;
import org.carecode.lims.libraries.QueryRecord;
import org.carecode.lims.libraries.ResultsRecord;

public class Indiko {

    static boolean testingPullingTestOrders = false;
    static boolean testingPushingTestResults = false;

    public static final Logger logger = LogManager.getLogger(Indiko.class);
    
    private static IndikoServer server = new IndikoServer(); // Make the server instance static for restart

    public static void main(String[] args) {
        if (testingPullingTestOrders) {
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
            QueryRecord queryRecord = new QueryRecord(0, "101010", null, null);
            logger.info("queryRecord=" + queryRecord);
            DataBundle pb = LISCommunicator.pullTestOrdersForSampleRequests(queryRecord);
            logger.info("pb = " + pb);
            System.exit(0);
        } else if (testingPushingTestResults) {
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
            DataBundle pdb = new DataBundle();
            PatientRecord patientRecord = new PatientRecord(0, "1212", "1212", "Buddhika", "Ari", "Male", "Sinhalese", "19750914", "Galle", "0715812399", "Niluka GUnasekara");

            pdb.setPatientRecord(patientRecord);

            ResultsRecord r1 = new ResultsRecord(1, "GLU", "112.0", "mg/dl", "202408172147", "Indigo", "0010");
            pdb.getResultsRecords().add(r1);

            QueryRecord qr = new QueryRecord(0, "1101", "1101", "");
            pdb.getQueryRecords().add(qr);

            LISCommunicator.pushResults(pdb);
            System.exit(0);
        }
        logger.info("Starting Indiko middleware...");
        try {
            logger.info("Loading settings...");
            SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load settings.", e);
            return;
        }

        int port = SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerPort();
        server = new IndikoServer();
        server.start(port);
    }

    public static void restartServer() {
        int port = SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerPort();
        try {
            logger.info("Stopping server...");
            server.stop(); // Implement the stop() method in IndikoServer to cleanly shut down the server

            logger.info("Waiting before restart...");
            Thread.sleep(2000); // Pause for 2 seconds before restarting

            logger.info("Restarting server on port " + port + "...");
            server = new IndikoServer(); // Create a new instance of the server
            server.start(port);
            logger.info("Server restarted successfully.");

        } catch (InterruptedException e) {
            logger.error("Error while restarting the server", e);
            Thread.currentThread().interrupt();
        }
    }

}
