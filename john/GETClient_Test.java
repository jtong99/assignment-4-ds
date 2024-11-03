import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class GETClient_Test {
    private GETClient client;
    private MockSocketServer mockSocket;

    @BeforeEach
    void setUp() {
        System.out.println("Running GETClient_Test");
        mockSocket = new MockSocketServer();
        client = new GETClient(mockSocket);
    }

    @Test
    void testSendRequest() {
        // Prepare mock response
        String mockResponse = "HTTP/1.1 200 OK\r\n" +
                              "Content-Type: application/json\r\n" +
                              "\r\n" +
                              "{\"id\":\"IDS60901\",\"name\":\"Test Station\",\"air_temp\":23.5}";
        mockSocket.setPreparedResponse(mockResponse);

        // Send request
        JsonObject response = client.sendRequest("localhost", 4567, "IDS60901");

        // Verify the request
        String sentRequest = mockSocket.getLastRequest();
        assertNotNull(sentRequest);
        assertTrue(sentRequest.startsWith("GET /data.json HTTP/1.1\r\n"));
        assertTrue(sentRequest.contains("LamportClock: "));
        assertTrue(sentRequest.contains("StationID: IDS60901"));

        // Verify the response
        assertNotNull(response);
        assertEquals("IDS60901", response.get("id").getAsString());
        assertEquals("Test Station", response.get("name").getAsString());
        assertEquals(23.5, response.get("air_temp").getAsDouble(), 0.001);
    }

    @Test
    void testSendRequestNoContent() {
        // Prepare mock response
        String mockResponse = "HTTP/1.1 204 No Content\r\n\r\n";
        mockSocket.setPreparedResponse(mockResponse);

        // Send request
        JsonObject response = client.sendRequest("localhost", 4567, null);

        // Verify the response
        assertNull(response);
    }

    @Test
    void testSendRequestServiceUnavailable() {
        // Prepare mock response
        String mockResponse = "HTTP/1.1 503 Service Unavailable\r\n\r\n";
        mockSocket.setPreparedResponse(mockResponse);

        // Send request
        JsonObject response = client.sendRequest("localhost", 4567, null);

        // Verify the response
        assertNull(response);
    }

    @Test
    void testGetServerInfo() {
        String[] result = GETClient.getServerInfo("http://example.com:8080");
        assertEquals("example", result[0]);
        assertEquals("8080", result[1]);

        result = GETClient.getServerInfo("example.com:8080");
        assertEquals("example", result[0]);
        assertEquals("8080", result[1]);
    }

    @Test
    void testGetServerInfoInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            GETClient.getServerInfo("invalid_url");
        });
    }
}