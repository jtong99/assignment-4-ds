public class ContentServer {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java ContentServer <serverHost:port> <dataFile>");
            return;
        }

        String[] serverInfo = args[0].split(":");
        if (serverInfo.length != 2) {
            System.err.println("Error: Server information should be in the format <serverHost:port>!");
            return;
        }

        String serverHost = serverInfo[0];
        int serverPort;
        try {
            serverPort = Integer.parseInt(serverInfo[1]);
        } catch (NumberFormatException e) {
            System.err.println("Error: Port number must be an integer!");
            return;
        }

        String dataFile = args[1];
        // Start Server
        new ContentServerUtils(serverHost, serverPort, dataFile).start();
    }
}
