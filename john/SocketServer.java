import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class SocketServer {
    private ServerSocket server;
    private Socket client;
    private PrintWriter outLog;
    private BufferedReader inLog;

    /**
     * Starts the server on the specified port.
     * Used by AggregationServer and MainAggregationServer to initialize their listening sockets.
     * @param port The port number to start the server on.
     */
    public void start(int port) {
        this.close();
        try {
            this.server = new ServerSocket(port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the server socket.
     * Called during shutdown procedures in various classes to ensure proper resource cleanup.
     */
    public void close() {
        try {
            if (this.server != null) this.server.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Accepts a client connection with a timeout.
     * Used by MainAggregationServer to handle incoming client connections.
     * @return A Socket object representing the client connection, or null if timeout occurs.
     * @throws IOException If an I/O error occurs.
     */
    public Socket accept() throws IOException {
        if (this.server == null || this.server.isClosed()) {
            throw new IllegalStateException("Server closed");
        }

        this.server.setSoTimeout(1000);
        try {
            return this.server.accept();
        } catch (SocketTimeoutException e) {
            if(Thread.currentThread().isInterrupted()){
                throw new IOException("Socket interrupted error", e);
            }
            return null;
        } catch (SocketException e) {
            if ("Socket closed".equals(e.getMessage())) {
                System.out.println("Server closed, no longer accepting connections");
                return null;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initializes a client socket connection and retrieves the Lamport clock value from the server.
     * Used by ContentServer and Client to establish a connection with the AggregationServer.
     * @param serverName The server's hostname.
     * @param portNumber The server's port number.
     * @return The Lamport clock value received from the server.
     */
    public int initializeSocketandGetLamport(String serverName, int portNumber) {
        this.close();
        try {
            this.client = new Socket(serverName, portNumber);
            
            this.outLog = new PrintWriter(client.getOutputStream(), true);
            
            this.inLog = new BufferedReader(new InputStreamReader(client.getInputStream()));
            
            String res = this.inLog.readLine();
            if (res == null) {
                throw new IOException("Server closed the connection unexpectedly.");
            }

            String[] resPlitted = res.split(":");
            String responseType = resPlitted[0].trim();
            String responseValue = resPlitted.length > 1 ? resPlitted[1].trim() : "";

            switch (responseType) {
                case "HTTP/1.1 503":
                    throw new IOException("Received 503 Service Unavailable from the server.");
                case "Lamport":
                    return Integer.parseInt(responseValue);
                default:
                    throw new IOException("Cannot get lamport, error: " + res);
            }
        } catch (IOException | RuntimeException e) {
            // e.printStackTrace();
            this.close(); 
            throw new RuntimeException("Error socket", e);
        }
    }

    /**
     * Sends a request to the server and returns the response.
     * Used by ContentServer for PUT requests and Client for GET requests.
     * @param serverName The server's hostname.
     * @param portNumber The server's port number.
     * @param data The request data to send.
     * @param isContentServer Boolean flag to differentiate between ContentServer and Client behavior.
     * @return The server's response as a String.
     */

    public String requestAndGetData(String serverName, int portNumber, String data, boolean isContentServer) {
        try {
            this.outLog.println(data);
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            int contentLength = 0;
            boolean isHeader = true;
            
            while (isHeader && (line = this.inLog.readLine()) != null) {
                if (line.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }

                responseBuilder.append(line).append("\r\n");

                if (line.isEmpty()) {
                    isHeader = false;
                }
            }

            if (!isContentServer && contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                this.inLog.read(bodyChars, 0, contentLength);
                responseBuilder.append(bodyChars);
            }
            return responseBuilder.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            this.close();
        }
    }

    /**
     * Reads the entire request from a client socket.
     * Used by AggregationServer to process incoming requests from clients and content servers.
     * @param clientSocket The client's socket connection.
     * @return The complete request as a String.
     */
    public String request(Socket clientSocket) {
        StringBuilder requestBuilder = new StringBuilder();

        try {
            InputStream input = clientSocket.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(input));
            
            String line;
            int contentLength = 0;
            boolean isHeader = true;
            while (isHeader && (line = in.readLine()) != null) {
                if (line.startsWith("Content-Length: ")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
                requestBuilder.append(line).append("\r\n");
                if (line.isEmpty()) {
                    isHeader = false;
                }
            }
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int bytesRead = 0;
                while (bytesRead < contentLength) {
                    int result = in.read(bodyChars, bytesRead, contentLength - bytesRead);
                    if (result == -1) {
                        break;
                    }
                    bytesRead += result;
                }
                requestBuilder.append(bodyChars);
            }
            
            return requestBuilder.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Initializes the server socket on a specified port.
     * This method seems redundant with the start() method and may not be used in the current implementation.
     * @param name The server name (unused parameter).
     * @param port The port number to start the server on.
     * @return Always returns 0.
     */
    public int init(String name, int port) {
        this.close();
        this.start(port);
        return 0;
    }

    /**
     * Sends a response to the client.
     * Used by AggregationServer to send responses back to clients and content servers.
     * @param response The response string to send.
     * @param clientSocket The client's socket connection.
     */
    public void response(String response, Socket clientSocket) {
        try {
            this.outLog = new PrintWriter(clientSocket.getOutputStream(), true);
            this.outLog.println(response);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.outLog.close();
        }
    }
}
