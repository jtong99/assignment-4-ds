import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DatabaseManagement_Test {
    private DatabaseManagement db;
    private final PrintStream originalOut = System.out;

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
    public void suppressOutput() {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                // Discard all data
            }
        }));
    }

    @AfterEach
    public void restoreOutput() {
        System.setOut(originalOut);
        clearJsonFiles();
    }

    @BeforeEach
    public void init() {
        db = db.initialize();
    }
    @AfterEach
    public void tearDown() {
        db.clear();
    }

    @Test
    public void testData() {
        String key = "TestStation1";
        WeatherFormat data = new WeatherFormat(1, "1", null);

        db.saveData(key, data);
        LinkedList<WeatherFormat> getData = db.getWeatherData(key);

        assertNotNull(getData);
        assertFalse(getData.isEmpty());
        assertEquals(data, getData.peek());
    }

    @Test
    public void testGetTime() {
        String key = "test1";
        long timestamp = System.currentTimeMillis();

        db.saveTime(key, timestamp);
        Long t = db.getSenderTimestamp(key);

        assertNotNull(t);
        assertEquals(timestamp, t);
    }
}
