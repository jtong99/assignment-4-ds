import java.io.IOException;
import java.net.*;
import java.io.*;

public class GETClient {

    // Declaring Lamport clock which will be used for logical timestamps
    private static long clientlamportClock = 1;

    /**
     * Main method for initializing the client, parsing the server URL,
     * creating the GET request, and sending it to the server.
     *
     * @param args Command-line arguments, expects the server URL as input.
     * @throws IOException if there is an issue during IO operations.
     */
    public static void main(String[] args) throws IOException {

        // Default values for host and port
        String host = "localhost";
        int port = 8080;
        String getRequest = "GET";

        // Ensure a server URL is passed as an argument
        if (args.length < 1) {
            System.exit(1);  // Exit if no arguments are provided
        }

        // Parse the server URL to extract host and port
        String[] host_port = parseServerUrl(args[0]);
        host = host_port[0];
        port = Integer.parseInt(host_port[1]);

        // Create the GET request with an incremented Lamport clock
        getRequest = createGetRequest(host, port, clientlamportClock + 1);

        // Send the GET request to the specified host and port
        sendGetRequest(host, port, getRequest);
    }

    /**
     * Sends the GET request to the server and processes the response, with retry logic.
     *
     * @param host The server host address.
     * @param port The server port number.
     * @param getRequest The GET request to be sent to the server.
     */
    public static void sendGetRequest(String host, int port, String getRequest) {
        int maxRetries = 5; // Maximum number of retry attempts
        int retryCount = 0; // Current retry attempt counter
        boolean isConnected = false; // Flag to track connection success

        while (retryCount < maxRetries && !isConnected) {
            try (Socket socket = new Socket(host, port)) {
                // Declare output and input streams for communication with the server
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Send the GET request to the server
                out.writeUTF(getRequest);
                out.flush();

                // Read and process the initial response
                String responseString = in.readUTF();
                processResponse(responseString);

                // Connection successful, set the flag to true
                isConnected = true;

                // Continuously listen for further responses from the server
                while (true) {
                    String response = in.readUTF();
                    processResponse(response);
                }
            } catch (IOException e) {
                // Handle connection errors and display the error message
                retryCount++;
                System.err.println("Connection error: " + e.getMessage());
                System.err.println("Retrying... Attempt " + retryCount + " of " + maxRetries);

                // Wait for a short time before retrying (optional delay between retries)
                try {
                    Thread.sleep(2000); // 2 seconds delay before retrying
                } catch (InterruptedException ie) {
                    // Handle interruption during sleep
                    System.err.println("Retry interrupted: " + ie.getMessage());
                }

                // If the retry count exceeds the maximum allowed, log the failure and exit
                if (retryCount == maxRetries) {
                    System.err.println("Max retries reached. Could not connect to the server.");
                }
            }
        }
    }
    /**
     * Processes the server's response and prints the content in a readable format.
     *
     * @param responseString The response from the server as a string.
     * @return The processed response data in formatted form.
     * @throws IOException if there is an issue during string operations.
     */
    public static String processResponse(String responseString) throws IOException {
        // Update the client's Lamport clock based on the response
        long clock = extractLamportClock(responseString);
        clientlamportClock = clock;
        String finalData = "";

        // Check if the response contains a 404 error
        if (responseString.contains("404")) {
            System.out.println("404 Not Found");
            return "404 Not Found";
        } else {
            // Extract the JSON part of the response
            int start = responseString.indexOf("{");
            int end = responseString.lastIndexOf("}");

            String jsonData = responseString.substring(start + 1, end);

            // Split the JSON data by commas and format it for output
            String[] jsonDataSplit = jsonData.split(",");

            for (String data : jsonDataSplit) {
                finalData += data.trim();
                finalData += "\n";
            }

            // Print the formatted response data
            System.out.println(finalData);
        }

        return finalData;
    }

    /**
     * Creates a GET request string to be sent to the server.
     *
     * @param host The server host address.
     * @param port The server port number.
     * @param lamportClockValue The Lamport clock value to be included in the request.
     * @return The formatted GET request string.
     */
    public static String createGetRequest(String host, int port, long lamportClockValue) {
        // Build and return the GET request string
        return "GET HTTP/1.1\nHost: " + host + " Port :" + port + " Lamport-Clock :" + lamportClockValue;
    }

    /**
     * Parses the server URL to extract the host and port.
     *
     * @param url The server URL.
     * @return A string array where the first element is the host and the second element is the port.
     */
    public static String[] parseServerUrl(String url) {
        String host;
        int port;

        // Remove the protocol (http://) if present in the URL
        if (url.startsWith("http://")) {
            url = url.substring(7);
        }

        // If the URL contains a port, split the host and port
        if (url.contains(":")) {
            String[] parts = url.split(":");
            host = parts[0]; // Host name
            port = Integer.parseInt(parts[1]); // Port number
        } else {
            // Default to port 4647 if none is provided
            host = url;
            port = 4647;
        }

        return new String[]{host, String.valueOf(port)};
    }

    /**
     * Extracts the Lamport clock value from the server's response.
     *
     * @param response The response string from the server.
     * @return The Lamport clock value, or -1 if not found or invalid.
     */
    public static long extractLamportClock(String response) {
        // Split the response into individual lines using CRLF as delimiter
        String[] lines = response.split("\r\n");

        // Iterate through each line to search for the "Lamport-Clock" header
        for (String line : lines) {
            // Check if the line starts with "Lamport-Clock:"
            if (line.startsWith("Lamport-Clock:")) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        // Parse and return the Lamport clock value
                        return Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        // Handle invalid Lamport clock values
                        System.out.println("Invalid Lamport clock value: " + parts[1]);
                        return -1;
                    }
                }
            }
        }

        // Return -1 if no valid Lamport clock is found
        return -1;
    }
}