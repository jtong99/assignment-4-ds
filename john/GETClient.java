import java.util.UUID;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;

public class GETClient {
    private SocketServer server;
    private String clientId;
    private Lamport clock;

    public GETClient(SocketServer server){
        this.server = server;
        this.clock = new Lamport();
        this.clientId = UUID.randomUUID().toString();
    }

    /**
     * Sends a request to the aggregation server and processes the response.
     * Implements retry logic and handles various server responses.
     * @param serverName The hostname of the aggregation server.
     * @param port The port number of the aggregation server.
     * @param stationID The ID of the weather station to query (can be null for all stations).
     * @return A JsonObject containing the weather data, or null if the request failed.
     */
    public JsonObject sendRequest(String serverName, int port, String stationID) {
        JsonObject response = null;
        int retry = 0;
        while (retry < 3) {
            try {
                int getLamportServer = server.initializeSocketandGetLamport(serverName, port);
                this.clock.adjust(getLamportServer);
                String getRequest = "GET /data.json HTTP/1.1\r\n" +
                                    "LamportClock: " + this.clock.getTime() + "\r\n" +
                                    "Source: " + this.clientId + "\r\n" +
                                    (stationID != null ? "StationID: " + stationID + "\r\n" : "") +
                                    "\r\n";
                String res = this.server.requestAndGetData(serverName, port, getRequest, false);
                System.out.println(res);
                if (res == null) {
                    System.out.println("Error: No response received from the server.");
                    System.out.println();
                    return null;
                }
    
                String[] responseLines = res.split("\r\n");
                String statusLine = responseLines.length > 0 ? responseLines[0] : "";
    
                switch (statusLine) {
                    case "HTTP/1.1 204 No Content":
                        System.out.println("Server response: No Content.");
                        System.out.println();
                        return null;
                    case "HTTP/1.1 503 Service Unavailable":
                        System.out.println("Server response: Service Unavailable.");
                        System.out.println();
                        return null;
                    default:
                        break;
                }
    
                response = JsonHandling.parseJSONObject(JsonHandling.extractJSONContent(res));
                return response;
            } catch (JsonParseException e) {
                System.out.println("Error parsing the server's JSON response: " + e.getMessage());
                return null;
            } catch(Exception e) {
                if (++retry < 3) {
                    System.out.println("Retrying request to server...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        System.out.println("Error sleeping thread: " + ex.getMessage());
                    }
                }else {
                    System.out.println("Error sending request to server: " + e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Gracefully shuts down the client.
     * Closes the associated socket connection.
     */
    public void shutdown() {
        System.out.println("Shutting down GETClient...");

        this.server.close();

        System.out.println("GETClient shutdown complete.");
    }

    /**
     * Parses the server information from a given domain string.
     * Extracts the server name and port number.
     * @param domain The domain string in the format "http://servername:port" or "servername:port".
     * @return An array containing the server name and port number.
     * @throws IllegalArgumentException if the domain format is invalid.
     */
    public static String[] getServerInfo(String domain){
        String splittedDomain = domain.replaceFirst("http://", "");
        String[] serverInfo = splittedDomain.split(":");
        if (serverInfo.length < 2 || serverInfo.length > 2) {
            throw new IllegalArgumentException("Invalid server domain format. Expected format: <server>:<port>");
        }

        String[] splittedServerInfo = serverInfo[0].split("\\.");
        return new String[] {splittedServerInfo[0], serverInfo[1]};
    }
    
    public static void main(String[] args) {
        String stationID = null;
        if (args.length == 2) {
            stationID = args[1];
        }
        String[] serverInfo = getServerInfo(args[0]);
        String serverName = serverInfo[0];
        int port = Integer.parseInt(serverInfo[1]);
        SocketServer socketServer = new SocketServer();
        GETClient client = new GETClient(socketServer);
        System.out.println("Connecting: " + serverName + ":" + port);
        JsonObject response = client.sendRequest(serverName, port, stationID);
        if (response != null) {
            try {
                String weatherData = JsonHandling.convertJSONToText(response);
                String[] lines = weatherData.split("\n");
                System.out.println();
                for (String line : lines) {
                    System.out.println(line);
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error while converting JSON to text.", e);
            }
        }
    }
}
