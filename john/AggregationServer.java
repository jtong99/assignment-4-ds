import java.io.*;
import com.google.gson.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AggregationServer
 */
public class AggregationServer {
    private SocketServer socketServer;
    private Lamport clock;
    private int port;
    private boolean isDown;
    private LinkedBlockingQueue<Socket> reqQueue;
    private static DatabaseManagement db = DatabaseManagement.initialize();
    private long EXPIRY = 40000; // 40 seconds

    public AggregationServer(SocketServer socketServer) {
        this.socketServer = socketServer;
        this.clock = new Lamport();
        this.reqQueue = new LinkedBlockingQueue<>();
    }

   

    /**
     * Checks if the server is currently running and accessible.
     * @return true if the server is up and responding, false otherwise.
     */
    public boolean isUp() {
        try {
            Socket ping = new Socket();
            System.out.println("ping to server: " + this.port);
            ping.connect(new InetSocketAddress("localhost", this.port), 1000); // 1 second timeout
            ping.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Starts the AggregationServer on the specified port.
     * Initializes the server socket and begins processing client requests.
     * @param port The port number on which to start the server.
     */
    public void start(int port) {
        System.out.println("AggregationServer started on: " + port);
        this.port = port;
        this.socketServer.start(port);
        try {
            while (!this.isDown) {
                Socket clientSocket = this.reqQueue.poll(10, TimeUnit.MILLISECONDS);
                if (clientSocket != null) {
                    handleData(clientSocket);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Accepts a new client connection and updates the Lamport clock.
     * @param clientSocket The newly connected client socket.
     * @return The updated Lamport clock time.
     */
    public int accept(Socket clientSocket) {
        try {
            System.out.println(this.port + " received socket: " + clientSocket);
            this.reqQueue.put(clientSocket);
            String c = "Lamport: " + this.clock.getTime();
            PrintWriter send = new PrintWriter(clientSocket.getOutputStream(), true);
            send.println(c);
            send.flush();
            this.clock.tick();
            return this.clock.getTime();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the current Lamport clock time of the server.
     * @return The current Lamport clock time.
     */
    public int getServerLamport(){
        return this.clock.getTime();
    }

    /**
     * Handles incoming data from a client socket.
     * Processes the request and sends an appropriate response.
     * @param clientSocket The client socket to handle.
     */
    public void handleData(Socket clientSocket) {
        try {
            String req = this.socketServer.request(clientSocket);
            System.out.println(req);
            if (req != null) {
                String responseData = normalizeReq(req);
                System.out.println("Response data to client: " + responseData);
                this.socketServer.response(responseData, clientSocket);
                
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Normalizes and processes the incoming request.
     * Determines the request type (GET or PUT) and calls appropriate handlers.
     * @param requestData The raw request data as a string.
     * @return The response to be sent back to the client.
    */
    public String normalizeReq(String requestData) {
        String[] lines = requestData.split("\r\n");
        String requestType = lines[0].split(" ")[0].trim();

        Map<String, String> headers = new HashMap<>();
        StringBuilder contentBuilder = new StringBuilder();

        boolean readingContent = false;

        for (int i = 1; i < lines.length; i++) {
            if (!readingContent) {
                if (lines[i].isEmpty()) {
                    readingContent = true;
                } else {
                    String[] headerParts = lines[i].split(": ", 2);
                    headers.put(headerParts[0], headerParts[1]);
                }
            } else {
                contentBuilder.append(lines[i]);
            }
        }

        String content = contentBuilder.toString();
        switch (requestType.toUpperCase()) {
            case "GET":
            return handleGetRequest(headers);
            case "PUT":
            return handlePutRequest(content, headers);
            default:
            return formatRes("400 Bad Request", null);
        }
    }

    /**
     * Handles PUT requests from content servers.
     * Updates the weather data in the database.
     * @param content The JSON content of the PUT request.
     * @param headers The headers of the PUT request.
     * @return The response to be sent back to the content server.
     */
    private String handlePutRequest(String content, Map<String, String> headers) {
        try {
            this.ensureClockConsistency();
            JsonObject jsonData = JsonHandling.convertObject(content, JsonObject.class);
            String id = getIdData(jsonData);
            if (id == null && id.isEmpty()) {
                return formatRes("500 Internal Server Error", null);
            }
            
            String source = headers.get("Source");
            long currTime = System.currentTimeMillis();
            Long latest = db.getSenderTimestamp(source);

            db.saveTime(source, currTime);
            WeatherFormat newWeatherData = new WeatherFormat(this.getLamport(headers), source, jsonData);
            db.saveData(id, newWeatherData);
            
            if (latest == null || (currTime - latest) > EXPIRY) {
                return formatRes("201 HTTP_CREATED", null);
            } else return formatRes("200 OK", null);
            
        } catch (Exception e) {
            System.out.println(e);
            return formatRes("500 Internal Server Error", null);
        }
    }

    /**
     * Ensures consistency of the Lamport clock with stored data.
     * @return true if the clock was adjusted, false otherwise.
     */
    private boolean ensureClockConsistency() {
        int highestStoredLamport = db.getHighestLamportClock();
        if (this.clock.getTime() <= 1 && highestStoredLamport > 1) {
            int consistentClock = Math.max(highestStoredLamport, this.clock.getTime());
            this.clock.adjust(consistentClock);
            System.out.println("Adjusted Lamport clock to: " + this.clock.getTime());
            return true;
        }
        return false;
    }

    /**
     * Handles GET requests from clients.
     * Retrieves and returns the requested weather data.
     * @param headers The headers of the GET request.
     * @return The response containing the requested weather data.
     */
    public String handleGetRequest(Map<String, String> headers) {
        boolean isUpdateLamport = this.ensureClockConsistency();
        int lamport = this.getLamport(headers);
        int updatedLamport = isUpdateLamport ? Math.max(this.clock.getTime(), lamport) : lamport;
        String stationId = headers.get("StationID") != null ? headers.get("StationID") : db.getStationID();
        if(stationId == null) return formatRes("204 No Content", null);
        System.out.println("Latest Station ID: " + stationId);
        LinkedList<WeatherFormat> data = db.getWeatherData(stationId);
        if(data == null) return formatRes("204 No Content", null);
        WeatherFormat latestData = data.stream().filter(d -> d.getLamport() <= updatedLamport).max(Comparator.comparingInt(WeatherFormat::getLamport)).orElse(null);
        System.out.println("Latest: " + latestData);
        
        if (latestData != null) {
            return formatRes("200 OK", latestData.getData());
        } else {
            return formatRes("204 No Content", null);
        }
    }

    /**
     * Extracts the station ID from the JSON data.
     * @param jsonData The JSON object containing weather data.
     * @return The extracted station ID.
     */
    public String getIdData(JsonObject jsonData) {
        return jsonData.has("id") ? jsonData.get("id").getAsString() : null;
    }

    /**
     * Retrieves the highest Lamport clock value from the database.
     * @return The highest Lamport clock value.
     */
    public int getHighestLamport(){
        return db.getHighestLamportClock();
    }

    /**
     * Retrieves the port of this server
     * @return port of current server
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Extracts and updates the Lamport clock value from request headers.
     * @param headers The request headers.
     * @return The updated Lamport clock value.
     */
    public int getLamport(Map<String, String> headers) {
        int lamport = Integer.parseInt(headers.getOrDefault("LamportClock", "-1"));
        this.clock.adjust(lamport);
        this.clock.tick();
        return lamport;
    }

    /**
     * Formats the HTTP response with appropriate headers and body.
     * @param status The HTTP status code and message.
     * @param jsonData The JSON data to include in the response body (if any).
     * @return The formatted HTTP response as a string.
     */
    private String formatRes(String status, JsonObject jsonData) {
        StringBuilder res = new StringBuilder();

        res.append("HTTP/1.1 ").append(status).append("\r\n");
        res.append("Lamport: ").append(this.clock.getTime()).append("\r\n");
        if (jsonData != null) {
            String prettyData = JsonHandling.prettier(jsonData);
            res.append("Content-Type: application/json\r\n");
            res.append("Content-Length: ").append(prettyData.length()).append("\r\n");
            res.append("\r\n");
            res.append(prettyData);
        } else {
            res.append("\r\n");
        }

        return res.toString();
    }

    /**
     * Stops the AggregationServer and releases resources.
     */
    public void stop() {
        this.isDown = true;
        this.socketServer.close();
        System.out.println("Stop AggregationServer on port " + this.port);
    }

    /**
     * clear data for testing
     */
    public void clearData(){
        db.clear();
    }

    public static void main(String[] args) {
        int port = 4000;
        SocketServer socketServer = new SocketServer();
        AggregationServer aggregationServer = new AggregationServer(socketServer);
        aggregationServer.start(port);
        
    }
}