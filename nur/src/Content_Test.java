import org.junit.*;
import org.mockito.*;
import java.io.*;
import java.net.*;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Content_Test {

    @Test
    public void testParseFile() throws IOException {
        // Create a temporary file for testing
        File tempFile = File.createTempFile("test", ".txt");
        try (PrintWriter writer = new PrintWriter(tempFile)) {
            writer.println("id: 123");
            writer.println("name: Test Name");
        }

        // Parse the file
        Map<String, String> result = ContentServer.parseFile(tempFile.getPath());

        // Assertions
        assertNotNull(result);
        assertEquals("123", result.get("id"));
        assertEquals("Test Name", result.get("name"));

        // Clean up the temp file
        tempFile.delete();
    }

    @Test
    public void testConvertMapToJson() {
        // Prepare data
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("name", "Test");
        dataMap.put("id", "123");

        // Convert to JSON
        String json = ContentServer.convertMapToJson(dataMap);

        // Assertions
        String expectedJson = "{\"name\": \"Test\", \"id\": \"123\"}";
        assertEquals(expectedJson, json);
    }

    @Test
    public void testCreatePutRequest() {
        // Prepare data
        String jsonData = "{\"id\": \"123\", \"name\": \"Test\"}";

        // Create PUT request
        String putRequest = ContentServer.createPutRequest(jsonData);

        // Assertions for headers and body content
        assertTrue(putRequest.contains("PUT /weather.json HTTP/1.1"));
        assertTrue(putRequest.contains("User-Agent: ATOMClient/1/0"));
        assertTrue(putRequest.contains("Content-Type: application/json"));
        assertTrue(putRequest.contains("Content-Length: " + jsonData.length()));
        assertTrue(putRequest.contains("Lamport-Clock:1"));
        assertTrue(putRequest.contains(jsonData));
    }

    @Test
    public void testSendPutRequest() throws IOException {
        // Mocking the socket and output/input streams
        Socket mockSocket = mock(Socket.class);
        DataOutputStream mockOut = mock(DataOutputStream.class);
        BufferedReader mockIn = mock(BufferedReader.class);

        when(mockSocket.getOutputStream()).thenReturn(mockOut);
        when(mockSocket.getInputStream()).thenReturn(mock(InputStream.class));
        when(mockIn.readLine()).thenReturn("HTTP/1.1 200 OK\r\nLamport-Clock:2\r\n");

        // Call the method under test
        String serverHost = "localhost";
        int serverPort = 8080;
        String putRequest = "PUT /weather.json HTTP/1.1\r\nContent-Length: 123\r\nLamport-Clock:1\r\n{\"id\":\"123\",\"name\":\"Test\"}";

        //Send request using mocked sockets
        BufferedReader response = ContentServer.sendPutRequest(mockIn, mockOut, putRequest);

        // Verify that the correct request was sent
        verify(mockOut).writeUTF(putRequest);
        verify(mockOut).flush();

        // Ensure that the Lamport clock was updated after reading the response
        assertEquals(2, ContentServer.contentLamportClock);
    }

    @Test
    public void testExtractLamportClock() {
        // Simulate a server response with the Lamport-Clock header
        String response = "HTTP/1.1 200 OK\r\nLamport-Clock:2\r\n";

        // Extract the Lamport clock
        long lamportClock = ContentServer.extractLamportClock(response);

        // Assertions
        assertEquals(2, lamportClock);
    }

}