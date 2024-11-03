import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class ContentServer_Test {
    private ContentServer contentServer;
    private MockSocketServer mockSocket;

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
        System.out.println("Running ContentServer_Test");
        mockSocket = new MockSocketServer();
        contentServer = new ContentServer(mockSocket);
    }

    @AfterEach
    void TearDown() {
        contentServer.shutdown();
        clearJsonFiles();
    }

    @Test
    void testIsLoadFileSuccess() {
        assertTrue(contentServer.isLoadFileSuccess("data1_0.txt"));
        assertFalse(contentServer.isLoadFileSuccess("non_existent_file.txt"));
    }

    @Test
    void testUploadData() {
        mockSocket.setLamportClockToReturn(1);
        mockSocket.setPreparedResponse("HTTP/1.1 201 Created\r\nLamport: 2\r\n\r\n");
        contentServer.isLoadFileSuccess("data1_0.txt");
        contentServer.uploadData("localhost", 4567);
        String sentData = mockSocket.getLastRequest();
        System.out.println(sentData);
        assertNotNull(sentData);
        assertTrue(sentData.startsWith("PUT /data.json HTTP/1.1\r\n"));
    }

    @Test
    void testRetryUpload() {
        mockSocket.setLamportClockToReturn(1);
        mockSocket.setPreparedResponse(null);  // First attempt fails
        contentServer.isLoadFileSuccess("data1_0.txt");
        contentServer.uploadData("localhost", 4567);
        assertTrue(mockSocket.getRequestCount() > 1);
    }

    @Test
    void testShutdown() {
        contentServer.shutdown();
        assertTrue(contentServer.isShutDown());
    }

    @Test
    public void testLoadWeatherData() {
        String testDataPath = "./data1_0.txt";
        contentServer.isLoadFileSuccess(testDataPath);
        assertNotNull(contentServer, "Weather loaded");
    }
}