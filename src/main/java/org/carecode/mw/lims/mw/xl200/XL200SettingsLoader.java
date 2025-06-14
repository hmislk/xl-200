package org.carecode.mw.lims.mw.xl200;

import com.google.gson.Gson;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.carecode.lims.libraries.MiddlewareSettings;

public class XL200SettingsLoader {

    private static final Logger logger = LogManager.getLogger(XL200SettingsLoader.class);
    private static MiddlewareSettings middlewareSettings;

    public static void loadSettings() {
        Gson gson = new Gson();
        try {
            // Read and print the contents of the config.json file
            String filePath = "D:\\ccmw\\settings\\xl200\\config.json";
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
            logger.debug("Contents of config.json:");
            logger.debug(jsonContent);

            // Now parse the JSON content
            try (FileReader reader = new FileReader(filePath)) {
                middlewareSettings = gson.fromJson(reader, MiddlewareSettings.class);
                logger.info("Settings loaded from config.json");

                // Debugging output
                logger.debug("MiddlewareSettings loaded:");
                logger.debug(middlewareSettings);

                if (middlewareSettings.getAnalyzerDetails() != null) {
                    logger.debug("Analyzer Name: " + middlewareSettings.getAnalyzerDetails().getAnalyzerName());
                } else {
                    logger.debug("AnalyzerDetails is null");
                }

                if (middlewareSettings.getLimsSettings() != null) {
                    logger.debug("LIMS Server Base URL: " + middlewareSettings.getLimsSettings().getLimsServerBaseUrl());
                } else {
                    logger.debug("LimsSettings is null");
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load settings from config.json", e);
            logger.error("Failed to load settings: " + e.getMessage());
        }
    }

    public static MiddlewareSettings getSettings() {
        if (middlewareSettings == null) {
            loadSettings();
        }
        return middlewareSettings;
    }
}
