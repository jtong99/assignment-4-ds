import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AggregationServer_Test {
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
    @BeforeEach
    void setUp() {
        System.out.println("Running AggregationServer_Test");
    }

    @AfterEach
    void clearData(){
        clearJsonFiles();
    }

    @Test
    void testHandleGetRequestWithoutData() {
        MockSocketServer mockSocket = new MockSocketServer();
        AggregationServer server = new AggregationServer(mockSocket);

        // Prepare a mock request
        mockSocket.setLastRequest("GET /data.json HTTP/1.1\r\nStationID: IDS60901\r\n\r\n");

        // Call the method we're testing
        server.handleData(new Socket()); // The Socket parameter doesn't matter for our mock

        // Check the response
        String response = mockSocket.getLastResponse();
        assertNotNull(response);
        System.out.println(response);
        assertTrue(response.contains("204 No Content"));
        server.stop();
    }

    @Test
    void testHandleGetRequestWithContent() {
        MockSocketServer mockSocket = new MockSocketServer();
        AggregationServer server = new AggregationServer(mockSocket);
    
        // First, simulate uploading some data
        String putRequest = "PUT /data.json HTTP/1.1\r\n" +
                            "Content-Length: 50\r\n" +
                            "LamportClock: 1\r\n" +
                            "Source: TestSource\r\n" +
                            "\r\n" +
                            "{\"id\":\"IDS60901\",\"name\":\"Test Station2\",\"air_temp\":20.0}";
        server.normalizeReq(putRequest);
    
        // Now prepare a GET request
        String getRequest = "GET /data.json HTTP/1.1\r\n" +
                            "StationID: IDS60901\r\n" +
                            "LamportClock: 2\r\n" +
                            "\r\n";
        mockSocket.setLastRequest(getRequest);
    
        // Call the method we're testing
        String response = server.normalizeReq(getRequest);
    
        // Check the response
        assertNotNull(response);
        System.out.println("Response: " + response);
        assertTrue(response.contains("200 OK"), "Expected 200 OK, but got: " + response);
        assertTrue(response.contains("IDS60901"), "Response should contain station data");
        assertTrue(response.contains("Test Station2"), "Response should contain station name");
        assertTrue(response.contains("20.0"), "Response should contain temperature data");
        server.stop();
    }

    @Test
    void testServerShutdown() throws IOException {
        MockSocketServer mockSocket = new MockSocketServer();
        AggregationServer server = new AggregationServer(mockSocket);
        
        // Start the server
        int port = 4567;
        new Thread(() -> server.start(port)).start();
        
        // Wait a bit for the server to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Verify the server is up
        assertTrue(server.isUp(), "Server should be up after starting");
        
        // Shutdown the server
        server.stop();
        
        // Wait a bit for the server to shut down
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Verify the server is down
        assertFalse(server.isUp(), "Server should be down after stopping");
        
        // Try to connect to the server (this should fail)
        assertThrows(IOException.class, () -> {
            new Socket("localhost", port);
        }, "Connecting to the server should throw an exception after shutdown");
    }
    private String extractJsonFromResponse(String response) {
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");
        if (jsonStart != -1 && jsonEnd != -1) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        throw new IllegalArgumentException("No valid JSON found in response");
    }
    @Test
    void testHandlePutRequest() {
        MockSocketServer mockSocket = new MockSocketServer();
        AggregationServer server = new AggregationServer(mockSocket);
        server.clearData();
        // Prepare a PUT request
        String putRequest = "PUT /data.json HTTP/1.1\r\n" +
                            "Content-Length: 123\r\n" +
                            "Content-Type: application/json\r\n" +
                            "LamportClock: 1\r\n" +
                            "Source: TestSource\r\n" +
                            "\r\n" +
                            "{\"id\":\"IDS60901\",\"name\":\"Test Station1\",\"state\":\"SA\",\"time_zone\":\"CST\",\"lat\":-34.9,\"lon\":138.6,\"local_date_time\":\"15/04:00pm\",\"air_temp\":23.5,\"wind_spd_kmh\":15}";

        String response = server.normalizeReq(putRequest);

        assertTrue(response.contains("201 HTTP_CREATED") || response.contains("200 OK"), 
                   "Expected 201 HTTP_CREATED or 200 OK, but got: " + response);

        String getRequest = "GET /data.json HTTP/1.1\r\n" +
                            "StationID: IDS60901\r\n" +
                            "LamportClock: 2\r\n" +
                            "\r\n";

        String getResponse = server.normalizeReq(getRequest);

        System.out.println("GET Response: " + getResponse);

        String jsonContent = extractJsonFromResponse(getResponse);
        JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();

        assertTrue(getResponse.contains("200 OK"), "Expected 200 OK for GET request");
        assertEquals("IDS60901", jsonObject.get("id").getAsString(), "GET response should contain the correct station ID");
        assertEquals("Test Station1", jsonObject.get("name").getAsString(), "GET response should contain the correct station name");
        assertEquals(23.5, jsonObject.get("air_temp").getAsDouble(), 0.001, "GET response should contain the correct temperature");
        assertEquals(15, jsonObject.get("wind_spd_kmh").getAsInt(), "GET response should contain the correct wind speed");
        server.stop();
    }
}
