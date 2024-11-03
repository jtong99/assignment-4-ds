import java.net.Socket;

public class MockSocketServer extends SocketServer {
    private String preparedResponse;
    private String lastRequest;
    private int lamportClockToReturn;
    private int requestCount;
    private boolean isClosed;

    public void setPreparedResponse(String response) {
        this.preparedResponse = response;
    }

    @Override
    public String request(Socket clientSocket) {
        // Instead of actually reading from a socket, just return the last set request
        return lastRequest;
    }

    @Override
    public void response(String response, Socket clientSocket) {
        // Instead of actually writing to a socket, just store the response
        this.preparedResponse = response;
    }

    public String getLastResponse() {
        return preparedResponse;
    }

    public void setLastRequest(String request) {
        this.lastRequest = request;
    }

    // New methods for ContentServer testing
    public void setLamportClockToReturn(int lamportClock) {
        this.lamportClockToReturn = lamportClock;
    }

    @Override
    public int initializeSocketandGetLamport(String serverName, int portNumber) {
        requestCount++;
        return lamportClockToReturn;
    }

    @Override
    public String requestAndGetData(String serverName, int portNumber, String data, boolean isContentServer) {
        lastRequest = data;
        requestCount++;
        return preparedResponse;
    }

    // @Override
    // public void start(int port) {
    //     isClosed = false;
    // }

    // @Override
    // public void close() {
    //     isClosed = true;
    // }

    public int getRequestCount() {
        return requestCount;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public String getLastRequest(){
        return this.lastRequest;
    }
}