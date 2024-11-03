
import java.io.*;
import java.net.*;
import java.util.*;
public class ContentServer {

    public static long contentLamportClock = 1;

    public static void main(String[] args) throws IOException{

        // Ensure that two arguments are provided: server URL and filename
        if (args.length != 2) {
            System.out.println("Server url or filename is missing");
            return;
        }

        // Extract server URL and filename from command line arguments
        String serverHost = args[0].split(":")[0];
        int serverPort = Integer.parseInt(args[0].split(":")[1]);
        String filePath = args[1];


        //Fetch the file from the filePath provided and convert it into a Json file and then send it to Server
        //construct the data format to be sent and send the a PUT request
        Map<String,String> dataMap = parseFile(filePath);
        if(!dataMap.containsKey("id"))
        {
            System.out.println("ID field not present");
        }
        String jsonObject = convertMapToJson(dataMap);


        String request = createPutRequest(jsonObject);

        // Initialize retry parameters
        int maxRetries = 5; // Maximum number of retries
        int attempt = 0; // Current attempt count
        boolean success = false; // Success flag for the request

        while (attempt < maxRetries && !success) {
            try (Socket socket = new Socket(serverHost, serverPort);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send the PUT request
                sendPutRequest(in, out, request);
                success = true; // Mark as successful if no exception is thrown

                // Optionally, read and print the server response (for debugging)
                String response = in.readLine(); // Read the first line of the response
                System.out.println("Response from server: " + response);

            } catch (IOException e) {
                attempt++; // Increment attempt count on failure
                System.out.println("Attempt " + attempt + " failed: " + e.getMessage());
                try {
                    Thread.sleep(3000); // 2 seconds delay before retrying
                } catch (InterruptedException ie) {
                    // Handle interruption during sleep
                    System.err.println("Retry interrupted: " + ie.getMessage());
                }
                if (attempt < maxRetries) {
                    System.out.println("Retrying...");
                } else {
                    System.out.println("Maximum attempts reached. Exiting.");
                }
                // Optional: Implement a backoff strategy here, e.g., Thread.sleep(1000); for 1 second delay
            }
        }

        // Loop to keep the application running (if necessary)
        while (true) {
            // You may want to handle incoming responses or other tasks here
        }



    }

    /**
     * Parses a file at the specified path and extracts key-value pairs
     * separated by a colon (":").
     *
     * @param filePath The path to the file to be parsed.
     * @return A Map containing the key-value pairs from the file, or
     *         null if the file is not found or an I/O error occurs.
     */
    public static Map<String, String> parseFile(String filePath) {
        Map<String, String> dataMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        dataMap.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + filePath);
            return null;
        } catch (IOException e) {
            System.out.println("Error reading file: " + e.getMessage());
            return null;
        }
        return dataMap;
    }

    /**
     * Converts a map of key-value pairs to a JSON string representation.
     *
     * @param dataMap A map containing key-value pairs to be converted to JSON.
     * @return A string representing the JSON format of the provided map.
     */
    public static String convertMapToJson(Map<String, String> dataMap) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");

        for (Iterator<Map.Entry<String, String>> it = dataMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, String> entry = it.next();
            jsonBuilder.append("\"").append(entry.getKey()).append("\": ")
                    .append("\"").append(entry.getValue()).append("\"");

            if (it.hasNext()) {
                jsonBuilder.append(", ");
            }
        }

        jsonBuilder.append("}");

        return jsonBuilder.toString();
    }

    /**
     * Constructs a PUT request for sending JSON data to the server.
     *
     * @param jsonData The JSON data to be included in the request body.
     * @return A formatted PUT request string.
     */
    public static String createPutRequest(String jsonData) {
        return String.format(
                "PUT /weather.json HTTP/1.1\r\n" +
                        "User-Agent: ATOMClient/1/0\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: %d\r\n" +
                        "Lamport-Clock:"+contentLamportClock+"\r\n" +
                        "%s",
                jsonData.length(), jsonData
        );
    }



    /**
     * Sends a PUT request to the specified server and returns the response reader.
     *
     * @param serverHost The hostname of the server.
     * @param serverPort The port number of the server.
     * @param putRequest The formatted PUT request to be sent.
     * @return A BufferedReader for reading the server's response, or null if an error occurs.
     */

    public static BufferedReader sendPutRequest(BufferedReader in, DataOutputStream out,String putRequest) throws IOException {
        try {

            // Send the PUT request
            out.writeUTF(putRequest);
            out.flush();

            // Read the response from the server
            String responseLine = in.readLine();
            if (responseLine != null) {

                if (responseLine.contains("200") || responseLine.contains("201"))
                {
                    System.out.println("Success: Response Code - " + responseLine);
                    long clock = extractLamportClock(responseLine);
                    System.out.println("Lamport-Clock = " + clock);
                    contentLamportClock = clock;
                } else {
                    System.out.println("Error: Response Code - " + responseLine);
                }
            }
            return in;
        } catch (IOException e) {
            System.out.println("Error sending PUT request: " + e.getMessage());
        }
        return null;
    }
    public static long extractLamportClock(String response) {
        // Split the response into individual lines using CRLF as delimiter
        String[] lines = response.split("\r\n");

        // Iterate through each line to search for the Lamport-Clock header
        for (String line : lines) {
            // Check if the line starts with "Lamport-Clock:"
            if (line.startsWith("Lamport-Clock:")) {
                // Split the line into key and value parts
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    try {
                        // Parse the Lamport clock value and return it
                        return Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        // Handle the case where the Lamport clock value is not a valid integer
                        System.out.println("Invalid Lamport clock value: " + parts[1]);
                    }
                }
            }
        }
        // Return null if the Lamport clock was not found or could not be parsed
        return -1;
    }






}