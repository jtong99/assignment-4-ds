import org.junit.Before;
import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class TestSynchronisation {

    @Before
    public void startServer() {
        // Start the Aggregation Server
        Thread aggregationServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                AggregationServer.main(new String[] { "8080" });
            }
        });
        aggregationServerThread.start();
    }

    @Test
    public void testSynchronisation() {

        // Start the Content Server
        ContentServer.main(new String[] { "localhost:8080", "src/weather_data.txt" });

        // Wait for the Content Server to send the data to the Aggregation Server
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify that the Aggregation Server has created data file
        File contentDataFile = new File("DataFiles/mainDatafile.data");
        assertTrue(contentDataFile.exists());

        // Verify that the GET Client received the data
        String expectedData = "Receiving data from server..\n" +
                "Connection Received..\n" +
                "Handling GET request..\n" +
                "Weather Data:\n" +
                "id: IDS60901\n" +
                "name: Adelaide (West Terrace /  ngayirdapira)\n" +
                "state: SA\n" +
                "time_zone: CST\n" +
                "lat: -34.9\n" +
                "lon: 138.6\n" +
                "local_date_time: 15/04:00pm\n" +
                "local_date_time_full: 20230715160000\n" +
                "air_temp: 13.3\n" +
                "apparent_t: 9.7\n" +
                "cloud: Partly cloudy\n" +
                "dewpt: 5.8\n" +
                "press: 1023.9\n" +
                "rel_hum: 61\n" +
                "wind_dir: S\n" +
                "wind_spd_kmh: 11\n" +
                "wind_spd_kt: 32\n" +
                "Lamport clock after GET request:6\n";

        // Create a test-specific output stream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        // Start the GET Client to send request
        GETClient.main(new String[] { "localhost:8080" });

        // Send updated PUT request with same content server ID
        ContentServer.main(new String[] { "localhost:8080", "src/Updated_weather_data.txt" });

        // Capturing Output of GETClient
        String receivedData = outputStream.toString();
        // String receivedData = System.out.toString();
        assertNotNull(receivedData);

        String receivedDataWithNewlines = receivedData.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        // Verify that the client received the data which was not updated
        assertTrue(receivedDataWithNewlines.contains(expectedData));
        // assertThat(receivedData, CoreMatchers.containsString(expectedData));

        // Restore the original output stream
        System.setOut(originalOut);

    }

}