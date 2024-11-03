
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)

public class EndToEndTest {

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
  public void endToEndDataAndClockTest() {

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

    // Start the GET Client
    GETClient.main(new String[] { "localhost:8080" });

    // Capturing output of GETClient
    String receivedData = outputStream.toString();
    // String receivedData = System.out.toString();
    assertNotNull(receivedData);

    String receivedDataWithNewlines = receivedData.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
    // Verify that the client received the data
    assertEquals(expectedData, receivedDataWithNewlines);
    // assertThat(receivedData, CoreMatchers.containsString(expectedData));

    // Restore the original output stream
    System.setOut(originalOut);

    // Verify that the Lamport time is correctly shared
    String lamportTime = receivedDataWithNewlines.split("Lamport clock after GET request:")[1].trim();

    // Content Server sends 1, Aggregation Server receives and increments to 2, and then
    // increments again to 3 when sending response to content server, getClient sends 1.
    // Aggregation server receives get and increments its time to 4, while sending response
    // increments to 5, get client receives and increments to its time to 6
    int expectedLamportTime = 6;
    assertEquals(expectedLamportTime, Integer.parseInt(lamportTime));

  }

  @Test
  public void serverDataRecordSizeTest() {

    // Verify that the Aggregation Server has created data file
    File contentDataFile = new File("DataFiles/mainDatafile.data");
    assertTrue(contentDataFile.exists());

    // Test 20 records scenario
    for (int i = 0; i < 25; i++) {
      ContentServer.main(new String[] { "localhost:8080", "src/weather_data.txt" });
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // Verify that the Aggregation Server has stored 20 records
    List<ContentData> contentDataList = new ArrayList<>();
    FileUtils.readDataFile(contentDataFile, contentDataList);
    assertEquals(20, contentDataList.size());

    // Remove data after test case
    try {
      PrintWriter writer = new PrintWriter(contentDataFile);
      writer.print("");
      writer.close();
    } catch (Exception e) {
      System.err.println("Failed to clear the file");
    }

  }

  @Test
  public void serverDataRecordWipeAfterForIdleContentServer() {

    // Verify that the Aggregation Server has created data file
    File contentDataFile = new File("DataFiles/mainDatafile.data");
    assertTrue(contentDataFile.exists());

    ContentServer.main(new String[] { "localhost:8080", "src/weather_data.txt" });

    // wait for data to be written to record
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // Data record is present before 30 seconds
    List<ContentData> contentDataList = new ArrayList<>();
    FileUtils.readDataFile(contentDataFile, contentDataList);
    assertEquals(1, contentDataList.size());

    // Test 30 seconds wipe scenario
    try {
      Thread.sleep(30000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Read from ContentDatafile again with empty list, Verify that the records with
    // the same ID are deleted
    contentDataList = new ArrayList<>();
    FileUtils.readDataFile(contentDataFile, contentDataList);
    assertEquals(0, contentDataList.size());

    // Remove data after test case
    try {
      PrintWriter writer = new PrintWriter(contentDataFile);
      writer.print("");
      writer.close();
    } catch (Exception e) {
      System.err.println("Failed to clear the file");
    }

  }

  @Test
  public void testServerResponseCodes() {

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outputStream)); // Capture System.out

    // Test 201 Created response
    ContentServer.main(new String[] { "localhost:8080", "src/weather_data.txt" });
    // Wait for the Content Server to send the data
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    String output = outputStream.toString(); // Capturing Output
    // Verify that the Aggregation Server returns a 201 Created response
    assertTrue(output.trim().contains("HTTP/1.1 201 Created"));

    // Test 500 invalid json Request response
    // Create a temporary file with invalid JSON data
    File invalidJsonFile = new File("invalid_json.txt");
    try (PrintWriter writer = new PrintWriter(invalidJsonFile)) {
      writer.println("Invalid JSON");
    } catch (FileNotFoundException e) {
      System.out.println("Error: Unable to create file 'invalid_json.txt'!");
      e.printStackTrace();
    }
    ContentServer.main(new String[] { "localhost:8080", "invalid_json.txt" });
    // Wait for the Content Server to send the data
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    output = outputStream.toString();
    // Verify that the Aggregation Server returns a 500 Request response
    assertTrue(output.trim().contains("HTTP/1.1 500 Internal Server Error"));

    // Test 200 OK response
    ContentServer.main(new String[] { "localhost:8080", "src/weather_data.txt" });
    // Wait for the GET Client to receive the data
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    output = outputStream.toString();
    // Verify that the Aggregation Server returns a 200 OK response
    assertTrue(output.trim().contains("HTTP/1.1 200 OK"));
    // assertEquals("HTTP/1.1 200 OK", output.trim());

  }

}