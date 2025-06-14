package org.carecode.mw.lims.mw.xl200;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class XL200 {

    private static final Logger logger = LogManager.getLogger(XL200.class);

    public static void main(String[] args) {
        logger.info("Starting XL200 middleware...");

        try {
            logger.info("Loading settings...");
            XL200SettingsLoader.loadSettings();
            logger.info("Settings loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load settings.", e);
            return;
        }

        XL200Server server = new XL200Server();
        server.start(); // Updated to match method with no parameters
    }
}
