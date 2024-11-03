
import com.google.gson.JsonObject;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static List<AggregationServer> servers = new ArrayList<>();
    public static SocketServer socketServer = new SocketServer();
    public static int port = 4567;
    public static ContentServer contentServer1, contentServer2;
    public static GETClient client1, client2;

    public static void setup(){
        for (int i = 1; i < 4; i++) {
            int serverPort = port + i;
            System.out.println("Server port: " + serverPort);
            SocketServer server = new SocketServer();
            AggregationServer aggregationServer = new AggregationServer(server);
            servers.add(aggregationServer);
            new Thread(() -> {
                aggregationServer.start(serverPort);
                try {
                    Thread.sleep(200);  // Sleep for 200 milliseconds after starting
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        MainAggregationServer mainAggregationServer = new MainAggregationServer(socketServer, servers);
        mainAggregationServer.start(port);
        SocketServer socketServer1 = new SocketServer();
        contentServer1 = new ContentServer(socketServer1);

        SocketServer socketServer2 = new SocketServer();
        contentServer2 = new ContentServer(socketServer2);

        SocketServer socketServer3 = new SocketServer();
        client1 = new GETClient(socketServer3);

        SocketServer socketServer4 = new SocketServer();
        client2 = new GETClient(socketServer4);
    }

    public static void test() {
        if (contentServer1.isLoadFileSuccess("data1_0.txt")) {
            contentServer1.uploadData("localhost", 4567);
        }
        if (contentServer1.isLoadFileSuccess("data1_1.txt")) {
            contentServer1.uploadData("localhost", 4567);
        }
        if (contentServer2.isLoadFileSuccess("data2_0.txt")) {
            contentServer2.uploadData("localhost", 4567);
        }
        if (contentServer2.isLoadFileSuccess("data2_1.txt")) {
            contentServer2.uploadData("localhost", 4567);
        }
        servers.get(0).stop();
        JsonObject res1 = client1.sendRequest("localhost", 4567, "IDS60901");
        JsonObject res2 = client2.sendRequest("localhost", 4567, "IDS60902");

        System.out.println( res1.get("lat").getAsString());
        System.out.println(res2.get("air_temp").getAsString());
    }

    public static void main(String[] args) {
        setup();
    }
}
