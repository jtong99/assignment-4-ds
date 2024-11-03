import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ContentServerUtils {
    private String dataFile;
    private LamportClock lamportClock = new LamportClock();
    private static final int POLLING_INTERVAL = 5;
    private static final int RECONNECT_INTERVAL = 10;
    private Socket socket;
    private PrintWriter outSocket;
    private BufferedReader inSocket;
    private long lastModifiedTime = 0;
    private String serverHost;
    private int serverPort;

    public ContentServerUtils(String serverHost, int serverPort, String dataFile) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.dataFile = dataFile;
    }

    /**
     * Starts the Content Server. It monitors the weather data file at regular intervals and sends the data
     * to the Aggregation Server whenever the file is modified. The server continues running and attempts to 
     * reconnect if there are issues with the connection.
     */
    public void start() {
        File file = new File(dataFile);
        if (!file.exists()) {
            System.err.println("Error: File " + dataFile + " does not exist!");
            return;
        }
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                if (file.lastModified() != lastModifiedTime) {
                    lastModifiedTime = file.lastModified();
                    System.out.println("File change detected!");
                    reconnectAndSendWeatherData();
                }
            }
        };
        executorService.scheduleAtFixedRate(task, 0, POLLING_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Attempts to reconnect to the Aggregation Server and send the weather data. 
     * If the connection fails, it retries after a delay.
     */
    private void reconnectAndSendWeatherData() {
        boolean success = false;
        while (!success) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                socket = new Socket(serverHost, serverPort);
                outSocket = new PrintWriter(socket.getOutputStream(), true);
                inSocket = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                sendWeatherData();
                success = true;
            } catch (IOException e) {
                System.out.println("Error: Failed to connect or send data! Retrying...");
                //e.printStackTrace(); //Commented to prevent extra printing
                try {
                    Thread.sleep(RECONNECT_INTERVAL * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Reads the weather data from the file, formats it as JSON, and sends it via a
     * PUT request
     * to the Aggregation Server. It also updates the Lamport clock based on the
     * server's response.
     */
    private void sendWeatherData() {
        try {
            // Read weather data from file and format it as JSON
            String jsonString = JsonUtils.parseFileToJson(dataFile);
            System.out.print("Sending data:");
            System.out.println(jsonString);
            // Prepare request headers
            String requestLine = "PUT /weather.json HTTP/1.1";
            String userAgent = "User-Agent: ATOMClient/1/0";
            String contentType = "Content-Type: application/json";
            String contentLength = "Content-Length: " + jsonString.length();
            String lamportValue = "Lamport-Time: " + lamportClock.tick();
            // Send PUT request
            outSocket.println(requestLine);
            outSocket.println(userAgent);
            outSocket.println(contentType);
            outSocket.println(contentLength);
            outSocket.println(lamportValue);
            outSocket.println(jsonString);
            outSocket.println();
            // Reading response and updating Lamport clock
            String responseLine;
            while ((responseLine = inSocket.readLine()) != null) {
                if (responseLine.isEmpty()) {
                    break;
                }
                if (responseLine.startsWith("Lamport-Time:")) {
                    int receivedLamportTime = Integer.parseInt(responseLine.split(":")[1].trim());
                    lamportClock.update(receivedLamportTime);
                }
                System.out.println(responseLine);
            }
            System.out.println("Lamport clock after PUT request:" + lamportClock.getTime());
        } catch (IOException e) {
            System.out.println("Server communication error!");
            e.printStackTrace();
        }
    }
}
