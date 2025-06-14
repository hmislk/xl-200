package org.carecode.mw.lims.mw.xl200;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class XL200AnalyzerCommunicator {

    private static final Logger logger = LogManager.getLogger(XL200AnalyzerCommunicator.class);

    private static final char ENQ = 0x05; // Enquiry
    private static final char ACK = 0x06; // Acknowledgement
    private static final char EOT = 0x04; // End of Transmission
    private static final char STX = 0x02; // Start of Text
    private static final char ETX = 0x03; // End of Text

    public static void startServer() {
        int middlewarePort = XL200SettingsLoader.getSettings().getAnalyzerDetails().getAnalyzerPort();

        try (ServerSocket serverSocket = new ServerSocket(middlewarePort)) {
            logger.info("Server started, listening on port: " + middlewarePort);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (Exception e) {
            logger.error("Error starting server", e);
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {
                StringBuilder messageBuffer = new StringBuilder();
                int charRead;
                while ((charRead = in.read()) != -1) {
                    char receivedChar = (char) charRead;
                    if (receivedChar == ENQ) {
                        // Respond with ACK
                        out.write(ACK);
                        out.flush();
                        logger.debug("Received ENQ, sent ACK");
                    } else if (receivedChar == EOT) {
                        // End of transmission, process message but keep connection open
                        String message = messageBuffer.toString();
                        logger.debug("Received message: " + message);
                        handleMessage(message, out);
                        messageBuffer.setLength(0); // Clear the buffer
                    } else {
                        // Append received character to buffer
                        messageBuffer.append(receivedChar);
                    }
                }
                } catch (Exception e) {
                    logger.error("Error handling client", e);
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        logger.error("Error closing client socket", e);
                    }
                }
        }
    }

    private static void handleMessage(String message, OutputStream out) {
        try {
            // Process message and determine appropriate response
            // For demonstration, just sending ACK after processing the message
            // In a real implementation, you might need to parse and handle the message appropriately

            // Send ACK after processing the message
            out.write(ACK);
            out.flush();
            logger.debug("Sent ACK after processing message");

        } catch (Exception e) {
            logger.error("Error handling message", e);
        }
    }
}
