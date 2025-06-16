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
    private static final String DEFAULT_CONFIG_PATH =
        "C\\CCMW\\SysmaxXS500i\\settings\\XL200\\config.json";

    public static void loadSettings() {
        Gson gson = new Gson();
        String filePath = System.getProperty(
            "xl200.config.path",
            System.getenv().getOrDefault("XL200_CONFIG_PATH", DEFAULT_CONFIG_PATH)
        );
        try {
            String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
            logger.debug("Contents of config.json:");
            logger.debug(jsonContent);

            // Now parse the JSON content
            try (FileReader reader = new FileReader(filePath)) {
                middlewareSettings = gson.fromJson(reader, MiddlewareSettings.class);
                logger.info("Settings loaded from " + filePath);

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
            logger.error("Failed to load settings from " + filePath, e);
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
