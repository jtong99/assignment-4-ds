import java.util.*;

import com.google.gson.JsonObject;

public class ContentServer {
    private Lamport clock;
    private SocketServer socketServer;
    private String source;
    private JsonObject data;
    private boolean isClosed;

    public ContentServer(SocketServer socket) {
        this.source = UUID.randomUUID().toString();
        this.socketServer = socket;
        this.clock = new Lamport();
    }

    /**
     * Attempts to load weather data from a specified file.
     * Converts the file content to JSON format.
     * @param filePath The path to the file containing weather data.
     * @return true if file loading and conversion succeed, false otherwise.
     */
    public boolean isLoadFileSuccess(String filePath) {
        try {
            String fileContent = JsonHandling.read(filePath);
            this.data = JsonHandling.convertTextToJson(fileContent);
            return true;
        } catch (Exception e) {
            System.out.println("Error on loading file " + e.getMessage());
            return false;
        }
    }

    /**
     * Uploads weather data to the aggregation server.
     * Implements retry logic and Lamport clock synchronization.
     * @param serverName The hostname of the aggregation server.
     * @param portNumber The port number of the aggregation server.
     */
    public void uploadData(String serverName, int portNumber) {
        try {
            System.out.println("Upload data: ");
            int lamportClockServer = this.socketServer.initializeSocketandGetLamport(serverName, portNumber);
            System.out.println("lamport data: ");
            this.clock.adjust(lamportClockServer);
            System.out.println("Updated Lamport clock 1: " + this.clock.getTime());
            String dataString = JsonHandling.prettier(this.data);
            
            String putRequest = "PUT /data.json HTTP/1.1\r\n" +
                            "Content-Length: " + dataString.length() + "\r\n" +
                            "LamportClock: " + this.clock.getTime() + "\r\n" +
                            "Source: " + this.source + "\r\n" +
                            "\r\n" +
                            dataString;
            String res = this.socketServer.requestAndGetData(serverName, portNumber, putRequest, true);
            System.out.println("Response data: ");
            System.out.println(res);
            if (res != null) {
                String[] lines = res.split("\r\n");
                for (String line : lines) {
                    if (line.startsWith("Lamport: ")) {
                        int serverClock = Integer.parseInt(line.split(": ")[1]);
                        this.clock.adjust(serverClock);
                        System.out.println("serverClock: " + serverClock);
                        System.out.println("Updated Lamport clock 2: " + this.clock.getTime());
                        break;
                    }
                }
                
                String statusCode = res.split(" ")[1];
                switch (statusCode) {
                    case "200":
                    case "201":
                        System.out.println("Weather data uploaded successfully.");
                        break;
                    case "503":
                        System.out.println("Service Unavailable.");
                        break;
                    case "500":
                        System.out.println("Technical error");
                        break;
                    default:
                        System.out.println("Unexpected response: " + res);
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("Retrying request to server...");
            retryUpload(serverName, portNumber);
        } 
    }

    /**
     * Implements retry logic for uploading data to the server.
     * Waits for a specified time before attempting to upload again.
     * @param serverName The hostname of the aggregation server.
     * @param portNumber The port number of the aggregation server.
     */
    public void retryUpload(String serverName, int portNumber) {
        try {
            Thread.sleep(5000);
            uploadData(serverName, portNumber);
        } catch (InterruptedException e) {
            System.out.println("Retry error: " + e.getMessage());
        }
    }

    /**
     * Get weather data
     * @return weather data
     */
    public JsonObject getWeatherData() {
        return this.data;
    }

    /**
     * Gracefully shuts down the ContentServer.
     * Closes the associated socket connection.
     */
    public void shutdown() {
        System.out.println("Shutting down ContentServer...");
        this.socketServer.close();
        this.isClosed = true;
        System.out.println("ContentServer shutdown complete.");
    }

    /**
     * get status of server
     * @return status of server
     */
    public boolean isShutDown(){
        return this.isClosed;
    }

    public static void main(String[] args) {
        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("No port provided");
            return;
        }
        String serverName = args[0];
        String file = args[2];

        SocketServer socketHandler = new SocketServer();
        ContentServer server = new ContentServer(socketHandler);

        if (!server.isLoadFileSuccess(file)) {
            System.out.println("Error: Failed to load data from " + file);
            return;
        }
        server.uploadData(serverName, port);

        Thread monitorThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                if ("SHUTDOWN".equalsIgnoreCase(input)) {
                    // shutdown();
                    scanner.close();
                    break;
                }
            }
        });
        monitorThread.start();
    }
}
