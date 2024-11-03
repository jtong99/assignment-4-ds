import java.io.*;
import java.net.Socket;
import java.util.UUID;

import static org.junit.Assert.*;
import java.util.*;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.Mockito.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class Aggregation_Test {
    static HashMap<Socket, Long> serverTime = new HashMap<>();
    private static final long TIMEOUT = 30000; // 30 seconds
    static HashMap<Socket, String> fileMap = new HashMap<>();
    private static AggregationServer server;
    private static Thread serverThread;
    private static int port = 6666;

    @BeforeClass
    public static void setUp() throws Exception {
        // Start the server in a separate thread
        server = new AggregationServer();
        serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{String.valueOf(port)});
            } catch (IOException e) {
                fail("Server failed to start: " + e.getMessage());
            }
        });
        serverThread.start();

        // Allow time for the server to start up
        Thread.sleep(1000);
    }

    /**
     * Test to load weather data from the latest JSON file.
     * It creates sample JSON files and checks if the latest data is loaded correctly.
     */
    @Test
    public void testLoadWeatherDataFromLatestFile() throws IOException {
        // Create the "data" folder
        File dataFolder = new File("test_data");
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }

        // Create sample JSON files directly in the test function
        String[] sampleFiles = {
                "test_data/weather_data_1.json",
                "test_data/weather_data_2.json",
                "test_data/weather_data_3.json"
        };

        String[] sampleContents = {
                "{\"weather\":\"sunny\"}",
                "{\"weather\":\"rainy\"}",
                "{\"weather\":\"cloudy\"}"
        };

        // Create the first two files
        for (int i = 0; i < 2; i++) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(sampleFiles[i]))) {
                writer.write(sampleContents[i]);
            }
        }

        // Wait to ensure the second file has a newer timestamp
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Create the third file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sampleFiles[2]))) {
            writer.write(sampleContents[2]);
        }

        // Call the function to load the latest file
        AggregationServer.loadWeatherDataFromLatestFile();

        // Assert that the weatherData list contains the content of the latest file
        assertFalse(AggregationServer.weatherData.isEmpty());
        assertEquals("{\"weather\":\"cloudy\"}", AggregationServer.weatherData.get(0));

        // Cleanup: Delete the test files after running the test
        for (File file : dataFolder.listFiles()) {
            file.delete();
        }
        dataFolder.delete();
    }



    /**
     * Test that the server responds with 404 when no data exists on a GET request.
     */
    @Test
    public void testGetRequestNoData() {

//        GETClient client = new GETClient();
//        String request = "GET HTTP/1.1\nHost: " + "localhost" + " Port :" + "8080" + " Lamport-Clock :"+"1";
//        client.sendGetRequest("localhost",8080,request);}
        try (
                Socket clientSocket = new Socket("localhost", port);
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
             DataInputStream in = new DataInputStream(clientSocket.getInputStream())
        ) {

            // Send a GET request
            String request = "GET HTTP/1.1\nHost: " + "localhost" + " Port :" + port + " Lamport-Clock :" + "1";
            out.writeUTF(request);
            out.flush();

            // Get the response from the server
            String response = in.readUTF();

            // Validate that the server responds with a 404 error and no data
            assertTrue(response.contains("HTTP/1.1 404 Not Found"));
            assertTrue(response.contains("No data"));

        } catch (IOException e) {
            fail("GET request failed: " + e.getMessage());
        }
    }

    /**
     * Test the client retry mechanism.
     * This test currently has no assertions and needs to be implemented further.
     */
    @Test
    public void testClientRetry() {

        GETClient client = new GETClient();
        String request = "GET HTTP/1.1\nHost: " + "localhost" + " Port :" + "8080" + " Lamport-Clock :"+"1";
        client.sendGetRequest("localhost",8080,request);}


    /**
     * Test for invalid HTTP requests to ensure the server responds appropriately.
     * This test checks for a 400 Bad Request response.
     */
    @Test
    public void testInvalidRequest() {
        try (Socket clientSocket = new Socket("localhost", port);
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
             DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {

            // Send a GET request
            String request = "DELETE HTTP/1.1\nHost: " + "localhost" + " Port :" + port + " Lamport-Clock :" + "1";
            out.writeUTF(request);
            out.flush();

            // Get the response from the server
            String response = in.readUTF();

            // Validate that the server responds with a 404 error and no data
            assertTrue(response.contains("HTTP/1.1 400 Bad Request"));
            assertTrue(response.contains("Invalid command"));

        } catch (IOException e) {
            fail("GET request failed: " + e.getMessage());
        }
    }

    /**
     * Test the GET request when valid data exists on the server.
     * It simulates a PUT request first to add data, followed by a GET request to retrieve it.
     */

    @Test
    public void testGetRequestWithData() {
        // First, simulate a PUT request to add data to the server
        try (Socket putClient = new Socket("localhost", port);
             DataOutputStream putOut = new DataOutputStream(putClient.getOutputStream());
             DataInputStream putIn = new DataInputStream(putClient.getInputStream())) {

            String jsonData = "{ \"temperature\": \"25°C\", \"humidity\": \"60%\" }";
            String putRequest = "PUT /weather HTTP/1.1\r\nLamport-Clock: 1\r\n\r\n" + jsonData;
            putRequest= String.format(
                    "PUT /weather.json HTTP/1.1\r\n" +
                            "User-Agent: ATOMClient/1/0\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: %d\r\n" +
                            "Lamport-Clock:"+"1"+"\r\n" +
                            "%s",
                    jsonData.length(), jsonData
            );
            putOut.writeUTF(putRequest);
            putOut.flush();

            // Read PUT response
            String putResponse = putIn.readUTF();
            assertTrue(putResponse.contains("HTTP/1.1 201 Created") || putResponse.contains("HTTP/1.1 200 OK"));

        } catch (IOException e) {
            fail("PUT request failed: " + e.getMessage());
        }

        // Now, simulate a GET request to retrieve the weather data
        try (Socket getClient = new Socket("localhost", port);
             DataOutputStream getOut = new DataOutputStream(getClient.getOutputStream());
             DataInputStream getIn = new DataInputStream(getClient.getInputStream())) {

            // Send a GET request
            String getRequest ="GET HTTP/1.1\nHost: " + "localhost" + " Port :" + port + " Lamport-Clock :" + "1";
            getOut.writeUTF(getRequest);
            getOut.flush();

            // Get the response from the server
            String getResponse = getIn.readUTF();

            // Validate the response contains the correct weather data and HTTP 200 OK
            assertTrue(getResponse.contains("HTTP/1.1 200 OK"));
            assertTrue(getResponse.contains("{ \"temperature\": \"25°C\", \"humidity\": \"60%\" }"));
            System.out.println(getResponse);

            // Ensure the Lamport clock is updated
            assertTrue(getResponse.contains("Lamport-Clock: 3"));

        } catch (IOException e) {
            fail("GET request failed: " + e.getMessage());
        }
    }





    /**
     * Test to check if the client can connect to the server and receive a response.
     */

    @Test
    public void testClientConnection() throws IOException {
        // Simulate a client connection
        try (Socket clientSocket = new Socket("localhost", 6666);
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
             DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {

            // Send a GET request
            out.writeUTF("GET HTTP/1.1\r\nHost: " + "host" + " Port :" + 6666 + "\r\n Lamport-Clock :" + 1);
            out.flush();

            // Read the server's response
            String response = in.readUTF();

            // Assert that the response contains the expected HTTP status
            assertEquals(true, response.contains("200") || response.contains("404"));


        }
    }

    // Test the processing of valid JSON data
    // Ensure that valid JSON is correctly processed
    @Test
    public void testProcessDataValid() {
        String result = AggregationServer.isJsonCorrect("Lamport-Clock: 0 {\"id\":\"123\",\"name\":\"Test\"}");
        assertEquals("{\"id\":\"123\",\"name\":\"Test\"}",result);

    }


    //Tests the scenario where corrupted  JSON is sent and a 500 error response is requested
    @Test
    public void testCorruptedData() {
        // First, simulate a PUT request to add data to the server
        try (Socket putClient = new Socket("localhost", port);
             DataOutputStream putOut = new DataOutputStream(putClient.getOutputStream());
             DataInputStream putIn = new DataInputStream(putClient.getInputStream())) {

            String jsonData = "\"temperature\": \"25°C\", \"humidity\": \"60%\" }";

            String putRequest = String.format(
                    "PUT /weather.json HTTP/1.1\r\n" +
                            "User-Agent: ATOMClient/1/0\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: %d\r\n" +
                            "Lamport-Clock:" + "1" + "\r\n" +
                            "%s",
                    jsonData.length(), jsonData
            );
            putOut.writeUTF(putRequest);
            putOut.flush();

            // Read PUT response
            String putResponse = putIn.readUTF();
            assertTrue(putResponse.contains("HTTP/1.1 500 Internal Server Error"));

        } catch (IOException e) {
            fail("PUT request failed: " + e.getMessage());
        }

    }

        // Test data storage and file creation
    // Ensure that data is stored correctly and files are created
    @Test
    public void testStoreData() throws IOException  {
        UUID serverId = UUID.randomUUID();
        String jsonData = "{\"id\":\"123\",\"name\":\"Test\"}";

        // Store the data
        int response = AggregationServer.storeJsonData(jsonData, serverId);


        assertEquals(0, response);


    }

    //Test if the Lamport clock checker function work properly
    @Test
    public void testValidLamportClock() {
        // Case with a valid Lamport-Clock header
        String response = "HTTP/1.1 200 OK\r\nLamport-Clock: 12345\r\nContent-Type: text/html\r\n\r\n";
        long lamportClock = AggregationServer.extractLamportClock(response);
        assertEquals(12345, lamportClock);
    }


    //Test if the Lamport clock checker function work properly
    @Test
    public void testInvalidLamportClockValue() {
        // Case where the Lamport-Clock header has an invalid value
        String response = "HTTP/1.1 200 OK\r\nLamport-Clock: abc\r\nContent-Type: text/html\r\n\r\n";
        long lamportClock = AggregationServer.extractLamportClock(response);
        assertEquals(-1, lamportClock); // Should return -1 as the clock value is invalid
    }


    //Tests if the cuntion to update client time in store properly
    @Test
    public void testStoreClientUpdateTime() throws Exception {
        // Create a mock socket
        Socket mockSocket = mock(Socket.class);
        SocketAddress mockAddress = new InetSocketAddress("127.0.0.1", 8080);
        when(mockSocket.getRemoteSocketAddress()).thenReturn(mockAddress);

        // Call the storeClientUpdateTime method
        int result = AggregationServer.storeClientUpdateTime(mockSocket);

        // Assert that the return value is 1
        assertEquals(1, result);


    }

    //Tests the logic that isused to remove the Content server that does not send data for more than 30 seconds
    @Test
    public void testRemoveInactiveClients() throws Exception {
        // Create mock Socket objects
        Socket activeClient = mock(Socket.class);
        Socket inactiveClient = mock(Socket.class);

        // Mock remote socket address for logging
        when(inactiveClient.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        // Add an active client and an inactive client to the serverTime map
        long currentTime = System.currentTimeMillis();
        AggregationServer.serverTime.put(activeClient, currentTime); // active client has current time
        AggregationServer.serverTime.put(inactiveClient, currentTime - (TIMEOUT + 1000)); // inactive client exceeded the timeout

        // Mock a filename for the inactive client
        String inactiveFilename = "testfile.txt";
        fileMap.put(inactiveClient, inactiveFilename);

        // Mock the file operations
        File mockFile = mock(File.class);
        when(mockFile.exists()).thenReturn(true);
        when(mockFile.delete()).thenReturn(true); // Assume the file is deleted successfully

        // Test the removal of inactive clients
        int result = AggregationServer.removeInactiveClients();


        // Assert that the flag is set to 1 due to successful file deletion
        assertEquals(0, result);

    }



}