

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

public class TestIntegration_Test {
    private MainAggregationServer mainAggregationServer;
    private List<AggregationServer> servers;
    private ContentServer contentServer1, contentServer2;
    private GETClient client1, client2;
    private PrintStream originalOut = System.out;
    private static final String DATA_DIR = "data";
    private static final String[] JSON_FILES = {"data.json", "sender.json"};

    private void clearJsonFiles() {
        for (String fileName : JSON_FILES) {
            Path filePath = Paths.get(DATA_DIR, fileName);
            try {
                if (Files.exists(filePath)) {
                    Files.write(filePath, new byte[0]);
                    System.out.println("Cleared contents of " + fileName);
                }
            } catch (IOException e) {
                System.err.println("Error clearing " + fileName + ": " + e.getMessage());
            }
        }
    }

    public void suppressOutput() {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                // Discard all data
            }
        }));
    }

    @BeforeEach
    public void setUpServer() {
        System.out.println("Running TestIntegration_Test");
        suppressOutput();
        SocketServer socketServer = new SocketServer();
        this.servers = new ArrayList<>();
        int port = 4567;
        for (int i = 1; i < 4; i++) {
            int serverPort = port + i;
            System.out.println("Server port: " + serverPort);
            SocketServer server = new SocketServer();
            AggregationServer aggregationServer = new AggregationServer(server);
            this.servers.add(aggregationServer);
            new Thread(() -> {
                aggregationServer.start(serverPort);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
        mainAggregationServer = new MainAggregationServer(socketServer, servers);
        mainAggregationServer.start(port);
        SocketServer socketServer1 = new SocketServer();
        this.contentServer1 = new ContentServer(socketServer1);

        SocketServer socketServer2 = new SocketServer();
        this.contentServer2 = new ContentServer(socketServer2);

        SocketServer socketServer3 = new SocketServer();
        this.client1 = new GETClient(socketServer3);

        SocketServer socketServer4 = new SocketServer();
        this.client2 = new GETClient(socketServer4);
    }

    @AfterEach
    public void shutDown() {
        this.mainAggregationServer.shutdown();
        this.servers.forEach(AggregationServer::stop);
        this.servers.forEach(AggregationServer::clearData);

        contentServer1.shutdown();
        contentServer2.shutdown();

        client1.shutdown();
        client2.shutdown();

        this.servers.clear();
        this.mainAggregationServer = null;
        this.contentServer1 = null;
        this.contentServer2 = null;
        this.client1 = null;
        this.client2 = null;

        System.setOut(originalOut);
        clearJsonFiles();
        try {
            Thread.sleep(1000);  // Sleep for 200 milliseconds after shutdown
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void testMultipleContentServerUploads() throws InterruptedException {
        // Upload from multiple content servers
        assertTrue(contentServer1.isLoadFileSuccess("data1_0.txt"));
        contentServer1.uploadData("localhost", 4567);
        Thread.sleep(1000);

        assertTrue(contentServer2.isLoadFileSuccess("data2_0.txt"));
        contentServer2.uploadData("localhost", 4567);
        Thread.sleep(1000);

        // Verify all data is accessible
        JsonObject res1 = client1.sendRequest("localhost", 4567, "IDS60901");
        JsonObject res2 = client2.sendRequest("localhost", 4567, "IDS60902");
        assertNotNull(res1);
        assertNotNull(res2);
        assertEquals("IDS60901", res1.get("id").getAsString());
        assertEquals("IDS60902", res2.get("id").getAsString());
    }

    

    @Test
    public void testReplicationServerFailover() throws InterruptedException {
        // Upload data
        assertTrue(contentServer1.isLoadFileSuccess("data1_0.txt"));
        contentServer1.uploadData("localhost", 4567);
        Thread.sleep(1000);

        // Shut down first replication server
        servers.get(0).stop();
        assertFalse(servers.get(0).isUp());

        // Verify data is still accessible from other servers
        JsonObject res = client1.sendRequest("localhost", 4567, "IDS60901");
        assertNotNull(res);
        assertEquals("IDS60901", res.get("id").getAsString());
    }

    /**
     * This is normal case test
     * CS1 uploaded to AS
     * CS1 uploaded to AS
     * CS2 uploaded to AS
     * CS2 uploaded to AS
     * 
     * Client1 request data for station IDS60901
     * Client2 request data for station IDS60902
     * 
     */
    @Test
    public void testNormalCase() throws InterruptedException {

        assertTrue(this.contentServer1.isLoadFileSuccess("data1_0.txt"));
        this.contentServer1.uploadData("localhost", 4567);
        
        
        Thread.sleep(1000);

        assertTrue(this.contentServer1.isLoadFileSuccess("data1_1.txt"));
        this.contentServer1.uploadData("localhost", 4567);

        Thread.sleep(1000);

        assertTrue(this.contentServer2.isLoadFileSuccess("data2_0.txt"));
        this.contentServer2.uploadData("localhost", 4567);

        Thread.sleep(1000);

        assertTrue(this.contentServer2.isLoadFileSuccess("data2_1.txt"));
        this.contentServer2.uploadData("localhost", 4567);
        
        Thread.sleep(1000);

        JsonObject res1 = this.client1.sendRequest("localhost", 4567, "IDS60901");

        Thread.sleep(1000);

        JsonObject res2 = this.client2.sendRequest("localhost", 4567, "IDS60902");

        assertTrue(this.servers.get(0).isUp());

        assertTrue(this.servers.get(1).isUp());

        assertTrue(this.servers.get(2).isUp());

        assertEquals("IDS60901", res1.get("id").getAsString());

        assertEquals("40", res1.get("lat").getAsString());

        assertEquals("IDS60902", res2.get("id").getAsString());

        assertEquals("20", res2.get("air_temp").getAsString());
    }

    @Test
    public void test1ASServerDown() throws InterruptedException {

        assertTrue(this.contentServer1.isLoadFileSuccess("data1_0.txt"));
        this.contentServer1.uploadData("localhost", 4567);
        
        
        Thread.sleep(1000);

        assertTrue(this.contentServer1.isLoadFileSuccess("data1_1.txt"));
        this.contentServer1.uploadData("localhost", 4567);

        Thread.sleep(1000);

        assertTrue(this.contentServer2.isLoadFileSuccess("data2_0.txt"));
        this.contentServer2.uploadData("localhost", 4567);

        Thread.sleep(1000);

        assertTrue(this.contentServer2.isLoadFileSuccess("data2_1.txt"));
        this.contentServer2.uploadData("localhost", 4567);
        
        Thread.sleep(1000);

        this.servers.get(0).stop();

        JsonObject res1 = this.client1.sendRequest("localhost", 4567, "IDS60901");

        Thread.sleep(1000);

        System.out.println(res1);

        JsonObject res2 = this.client2.sendRequest("localhost", 4567, "IDS60902");

        assertFalse(this.servers.get(0).isUp());

        assertTrue(this.servers.get(1).isUp());

        assertTrue(this.servers.get(2).isUp());

        assertEquals("IDS60901", res1.get("id").getAsString());

        assertEquals("40", res1.get("lat").getAsString());

        assertEquals("IDS60902", res2.get("id").getAsString());

        assertEquals("20", res2.get("air_temp").getAsString());
    }

    @Test
    public void testServerDown() {
        servers.forEach(AggregationServer::stop);

        assertTrue(this.contentServer1.isLoadFileSuccess("data1_0.txt"));
        Thread uploadThread = new Thread(() -> {
            this.contentServer1.uploadData("localhost", 4567);
        });

        uploadThread.start();

        try {
            uploadThread.join(10000); // 10 seconds timeout
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    
        // Interrupt the upload thread if it's still running
        if (uploadThread.isAlive()) {
            uploadThread.interrupt();
            System.out.println("Upload operation timed out after 10 seconds");
        }

        JsonObject res1 = this.client1.sendRequest("localhost", 4567, "IDS60901");
        assertNull(res1);
    }

    @Test
    public void testDataExpirationAfter30Seconds() throws InterruptedException {
        // Upload data
        assertTrue(this.contentServer1.isLoadFileSuccess("data2_0.txt"));
        this.contentServer1.uploadData("localhost", 4567);

        // Wait for a short time to ensure data is processed
        Thread.sleep(1000);

        // Verify data was uploaded successfully
        JsonObject initialResponse = this.client1.sendRequest("localhost", 4567, "IDS60902");
        assertNotNull(initialResponse);
        assertEquals("IDS60902", initialResponse.get("id").getAsString());

        // Wait for 31 seconds (slightly more than the 30-second expiration time)
        Thread.sleep(35000);

        // Check if data has been deleted
        JsonObject afterExpirationResponse = this.client1.sendRequest("localhost", 4567, "IDS60902");
        assertNull(afterExpirationResponse, "Data should have been deleted after 30 seconds of inactivity");
    }
}