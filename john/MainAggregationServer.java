import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;


public class MainAggregationServer {
    private static final int PORT = 4567;
    private int serverIndex = 0;
    private SocketServer socketServer;
    private List<AggregationServer> servers;
    private boolean isDown;
    private static final String LAMPORT_FILE_PATH = "data" + File.separator + "lamport.json";
    private Lamport globalLamport = new Lamport();
    public MainAggregationServer(SocketServer server, List<AggregationServer> servers) {
        this.socketServer = server;
        this.servers = servers;
    }

    /**
     * Starts the MainAggregationServer on the specified port.
     * Initializes the server socket and begins accepting client connections.
     * @param port The port number on which to start the server.
     */
    public void start(int port) {
        this.socketServer.start(port);
        this.initializeAcceptThread();
    }

    /**
     * Initializes a separate thread for accepting client connections.
     * This allows the server to handle multiple connections concurrently.
     */
    private void initializeAcceptThread() {
        new Thread(() -> {
            while (!isDown) {
                try {
                    Socket clientSocket = socketServer.accept();
                    if (clientSocket != null) {
                        handleClientSocket(clientSocket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Retrieves an active AggregationServer from the list of managed servers.
     * Implements a simple round-robin load balancing strategy.
     * @return An active AggregationServer, or null if no servers are available.
     */
    public AggregationServer getActiveServer() {
        if (this.servers.isEmpty()) {
            return null;
        }
        AggregationServer nextServer;

        nextServer = this.servers.get(serverIndex);
        for (int i = 0; i < this.servers.size(); i++) {
            if (nextServer.isUp()) {
                return nextServer;
            }
            serverIndex = (serverIndex + 1) % this.servers.size();
            nextServer = this.servers.get(serverIndex);
        }

        return null;
    }

    /**
     * Handles a new client socket connection.
     * Delegates the connection to an active AggregationServer if available.
     * @param client The newly connected client socket.
     */
    public void handleClientSocket(Socket client){
        try {
            AggregationServer activeServer = getActiveServer();
            
            if (activeServer != null) {
                int latestLamportAS = activeServer.accept(client);
                this.globalLamport.adjust(latestLamportAS);
            } else {
                String res = "HTTP/1.1 503 Service Unavailable\r\n" +
                                        "Lamport: -1\r\n" +
                                        "\r\n";
                this.socketServer.response(res, client);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gracefully shuts down the MainAggregationServer and all managed AggregationServers.
     * Closes all associated socket connections.
     */
    public void shutdown() {
        System.out.println("Shutting down main...");
        this.isDown = true;

        for (AggregationServer server : this.servers) {
            server.stop();
        }

        this.socketServer.close();

        System.out.println("LoadBalancer and all managed AggregationServers have been shut down.");
    }
    public static void main(String[] args) {
      SocketServer socket = new SocketServer();
      List<AggregationServer> servers = new ArrayList<>();
      int port = PORT;
      for (int i = 1; i < 4; i++) {
        int serverPort = port + i;
        SocketServer aggreSocket = new SocketServer();
        AggregationServer aggreServer = new AggregationServer(aggreSocket);
        servers.add(aggreServer);
        new Thread(() -> {
          aggreServer.start(serverPort);
        }).start();
      }
      MainAggregationServer mainServer = new MainAggregationServer(socket, servers);
      mainServer.start(port);
    }
}
