import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

public class GETClient {
    private static final int RECONNECT_INTERVAL = 10;
    private static Socket socket;
    private static PrintWriter outSocket;
    private static BufferedReader inSocket;
    private static String serverHost;
    private static int serverPort;
    private static String stationId = null;
    private static LamportClock lamportClock = new LamportClock();

    public static void main(String args[]) {
        if (args.length == 0) {
            System.out.println("Usage java GETClient <server-host:port> <station-id>(Optional)");
            return;
        }

        String[] serverInfo = args[0].split(":");
        if (serverInfo.length != 2) {
            System.err.println("Error: Server information should be in the format <serverHost:port>");
            return;
        }
        serverHost = serverInfo[0];
        try {
            serverPort = Integer.parseInt(serverInfo[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Port number must be an integer.");
            return;
        }

        if (args.length == 2) {
            stationId = args[1];
        }

        reconnectGetWeatherData();
    }

    /**
     * Continuously attempts to reconnect to the server and retrieve weather data.
     * If a connection is established, it fetches the weather data by calling the
     * getWeatherData() method.
     */
    private static void reconnectGetWeatherData() {
        boolean success = false;
        while (!success) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                socket = new Socket(serverHost, serverPort);
                outSocket = new PrintWriter(socket.getOutputStream(), true);
                inSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                getWeatherData();
                success = true;
            } catch (IOException e) {
                System.out.println("Error: Failed to connect!! Retrying...");
                //e.printStackTrace();  //commented to prevent extra printings
                try {
                    Thread.sleep(RECONNECT_INTERVAL * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Sends an HTTP GET request to the server to retrieve weather data in JSON format.
     * The response is parsed and displayed, and the Lamport clock is updated.
     */
    private static void getWeatherData() {
        try {
            System.out.println("Receiving data from server..");
            // Prepare request headers
            String requestLine = "GET /weather.json" + (stationId != null ? "?stationId=" + stationId : "")
                    + " HTTP/1.1";
            String userAgent = "User-Agent: ATOMClient/1/0";
            String contentType = "Content-Type: application/json";
            String lamportValue = "Lamport-Time: " + lamportClock.tick();
            // Send GET request
            outSocket.println(requestLine);
            outSocket.println(userAgent);
            outSocket.println(contentType);
            outSocket.println(lamportValue);
            outSocket.println();
            // Reading response and updating Lamport clock
            Map<String, String> json = null;
            String responseCode = null;
            String responseLine;
            while ((responseLine = inSocket.readLine()) != null) {
                if (responseLine.isEmpty()) {
                    break;
                }
                if (responseLine.startsWith("Lamport-Time:")) {
                    int receivedLamportTime = Integer.parseInt(responseLine.split(":")[1].trim());
                    lamportClock.update(receivedLamportTime);
                }
                if (responseLine.startsWith("{") && responseLine.endsWith("}")) {
                    json = JsonUtils.parseJSON(responseLine);
                }
                if (responseLine.startsWith("HTTP/1.1")) {
                    responseCode = responseLine.split(" ")[1];
                }
            }

            // Define the order of keys to print
            String[] order = {
                    "id", "name", "state", "time_zone", "lat", "lon",
                    "local_date_time", "local_date_time_full", "air_temp",
                    "apparent_t", "cloud", "dewpt", "press", "rel_hum",
                    "wind_dir", "wind_spd_kmh", "wind_spd_kt"
            };
            /*if (json != null && !json.isEmpty()) {
                System.out.println(" Weather Data:");
                for (Map.Entry<String, String> item : json.entrySet()) {
                    System.out.println(item.getKey() + ": " + item.getValue());
                }
            }*/
            
            // Print JSON data in the specified order
            if (json != null && !json.isEmpty()) {
                System.out.println("Weather Data:");
                for (String key : order) {
                    if (json.containsKey(key)) {
                        System.out.println(key + ": " + json.get(key));
                    }
                }
            } else {
                if ("400".equals(responseCode) || "500".equals(responseCode)) {
                    System.out.println("Error occured while processing request!");
                } else if ("404".equals(responseCode)) {
                    System.out.println("No recorded data in Server");
                }
            }
            System.out.println("Lamport clock after GET request:" + lamportClock.getTime());
        } catch (Exception e) {
            System.out.println("Server communication error!");
            e.printStackTrace();
        }
    }
}
