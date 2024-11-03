import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class GETClient_Test {

    // Test for processing a valid JSON response and a 404 response
    @Test
    public void testProcessResponseValidJson() throws IOException {
        // Arrange: Valid JSON and 404 response samples
        String validResponse = "HTTP/1.1 200 OK\r\nLamport-Clock: 5\r\n\r\n{\"temperature\":25, \"humidity\":60}";
        String notFoundResponse = "HTTP/1.1 404 Not Found\nNo data";

        // Act: Call the processResponse function with the valid response
        String result = GETClient.processResponse(validResponse);

        // Assert: Verify the output for the valid JSON response
        String expectedOutput = "\"temperature\":25\n\"humidity\":60\n";
        assertEquals(expectedOutput, result);

        // Act: Call the processResponse function with the 404 response
        result = GETClient.processResponse(notFoundResponse);

        // Assert: Verify the output for the 404 response
        String notFoundOutput = "404 Not Found";
        assertEquals(notFoundOutput, result);
    }

    // Test for the parseServerUrl method when a port is provided
    @Test
    public void testParseServerUrlWithPort() {
        // Act: Call the parseServerUrl method with a host and port
        String[] result = GETClient.parseServerUrl("http://localhost:8080");

        // Assert: Check that the host and port are correctly parsed
        assertEquals("localhost", result[0]);
        assertEquals("8080", result[1]);
    }

    // Test for the parseServerUrl method when no port is provided (default port scenario)
    @Test
    public void testParseServerUrlWithoutPort() {
        // Act: Call the parseServerUrl method with only the host
        String[] result = GETClient.parseServerUrl("http://localhost");

        // Assert: Check that the host is parsed correctly and default port is used
        assertEquals("localhost", result[0]);
        assertEquals("4647", result[1]); // Default port 4647 should be used
    }

    // Test for creating a valid GET request string
    @Test
    public void testCreateGetRequest() {
        // Act: Call createGetRequest with a host, port, and Lamport clock value
        String getRequest = GETClient.createGetRequest("localhost", 8080, 10);

        // Assert: Check that the GET request is properly formatted
        String expectedRequest = "GET HTTP/1.1\nHost: localhost Port :8080 Lamport-Clock :10";
        assertEquals(expectedRequest, getRequest);
    }

    // Test for extracting a valid Lamport clock from the response
    @Test
    public void testExtractLamportClockValid() {
        // Arrange: A sample response containing a valid Lamport clock
        String response = "HTTP/1.1 200 OK\r\nLamport-Clock: 15\r\nContent-Type: application/json\r\n";

        // Act: Call extractLamportClock to get the clock value
        long lamportClock = GETClient.extractLamportClock(response);

        // Assert: Check that the clock value is extracted correctly
        assertEquals(15, lamportClock);
        // Arrange: A sample response containing a valid Lamport clock
        String Invalidresponse = "HTTP/1.1 200 OK\r\nLamport-Clock: AB\r\nContent-Type: application/json\r\n";

        // Act: Call extractLamportClock to get the clock value
        lamportClock = GETClient.extractLamportClock(Invalidresponse);

        // Assert: Check that the clock value is extracted correctly
        assertEquals(-1, lamportClock);
    }

    // Test for extracting an invalid Lamport clock (non-numeric)
    @Test
    public void testExtractLamportClockInvalid() {
        // Arrange: A sample response with an invalid Lamport clock value (non-numeric)
        String response = "HTTP/1.1 200 OK\r\nLamport-Clock: abc\r\nContent-Type: application/json\r\n";

        // Act: Call extractLamportClock and expect it to handle the invalid value
        long lamportClock = GETClient.extractLamportClock(response);

        // Assert: Check that the method returns -1 for an invalid clock
        assertEquals(-1, lamportClock);
    }

    // Test for handling a response with no Lamport clock
    @Test
    public void testExtractLamportClockNotFound() {
        // Arrange: A sample response without a Lamport clock header
        String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n";

        // Act: Call extractLamportClock and expect it to return -1 (clock not found)
        long lamportClock = GETClient.extractLamportClock(response);

        // Assert: Check that the method returns -1 when the clock header is missing
        assertEquals(-1, lamportClock);
    }
}