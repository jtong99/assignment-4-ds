import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

public class DatabaseManagement {
    private static final long EXPIRE_SAVE = 30000;

    private static final String MAIN_DATA = "data" + File.separator + "data.json";
    private static final String MAIN_DATA_BACKUP = "data" + File.separator + "data_backup.json";
    private static final String SENDER_FILE_PATH = "data" + File.separator + "sender.json";
    private static final String SENDER_FILE_PATH_BACKUP = "data" + File.separator + "sender_backup.json";
    private String latestStationID;
    private Map<String, LinkedList<WeatherFormat>> weatherData = new ConcurrentHashMap<>();
    private Map<String, Long> senderTimestamp = new ConcurrentHashMap<>();
    private static DatabaseManagement db;
    private ScheduledExecutorService updateDataSchedule = Executors.newScheduledThreadPool(1);

    /**
     * Singleton instance getter for DatabaseManagement.
     * Ensures only one instance of the database is created and used throughout the application.
     * @return The singleton instance of DatabaseManagement.
     */
    private DatabaseManagement() {
        if (db != null) {
            throw new RuntimeException("use get db method");
        }
        this.loadData();
        updateDataSchedule.scheduleAtFixedRate(this::updateData, 0, 5, TimeUnit.SECONDS);
    }

    public static DatabaseManagement initialize() {
        if (db == null) {
            db = new DatabaseManagement();
        }
       return db;
    }
    

    /**
     * Loads data from persistent storage into memory.
     * Retrieves weather data and sender timestamps from JSON files, handling potential file errors.
     */
    public void loadData() {
        Map<String, LinkedList<WeatherFormat>> loadedWeatherData = loadDataFromFile(MAIN_DATA, MAIN_DATA_BACKUP, 
            new TypeToken<ConcurrentHashMap<String, LinkedList<WeatherFormat>>>(){}.getType());

        Map<String, Long> loadedSenderTimestamp = loadDataFromFile(SENDER_FILE_PATH, SENDER_FILE_PATH_BACKUP, 
            new TypeToken<ConcurrentHashMap<String, Long>>(){}.getType());

        if (loadedWeatherData != null) {
            this.weatherData = loadedWeatherData;
        }

        if (loadedSenderTimestamp != null) {
            this.senderTimestamp = loadedSenderTimestamp;
        }
    }

    /**
     * Retrieves the highest Lamport clock value from stored weather data.
     * Used to maintain clock consistency across the distributed system.
     * @return The highest Lamport clock value found in the stored data.
     */
    public int getHighestLamportClock() {
        int highestLamport = 0;
        for (LinkedList<WeatherFormat> dataList : weatherData.values()) {
            for (WeatherFormat data : dataList) {
                highestLamport = Math.max(highestLamport, data.getLamport());
            }
        }
        return highestLamport;
    }

    /**
     * Generic method to load data from a file with error handling and backup support.
     * @param filePath The primary file path to load from.
     * @param backupFilePath The backup file path to use if the primary fails.
     * @param type The type of data to deserialize into.
     * @return The deserialized data of type T, or null if loading fails.
     */
    private <T> T loadDataFromFile(String filePath, String backupFilePath, Type type) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
                Files.write(Paths.get(filePath), "{}".getBytes());
            }

            String jsonData = new String(Files.readAllBytes(Paths.get(filePath)));
            return JsonHandling.convertObject(jsonData, type);
        } catch (IOException e) {
            System.out.println("Error reading from main file: " + e.getMessage());
            System.out.println("Attempting to read from backup file...");
            try {
                String backupData = new String(Files.readAllBytes(Paths.get(backupFilePath)));
                return JsonHandling.convertObject(backupData, type);
            } catch (IOException ex) {
                System.out.println("Error reading from backup file: " + ex.getMessage());
                return null;
            }
        }
    }

    /**
     * Saves new weather data to the database.
     * Updates both in-memory data structures and persists to file.
     * @param key The station ID or unique identifier for the weather data.
     * @param data The WeatherFormat object containing the new data.
     */
    public void saveData(String key, WeatherFormat data) {
        try {
            this.weatherData.computeIfAbsent(key, e -> new LinkedList<>()).add(data);
            this.latestStationID = key;
            this.saveWeatherData();
            this.saveSenderData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Persists the current weather data to file.
     * Ensures data durability across server restarts.
     */
    public void saveWeatherData() {
        try {
            String jsonWeatherData = JsonHandling.convertJSON(this.weatherData);
            Files.write(Paths.get(MAIN_DATA_BACKUP), jsonWeatherData.getBytes());
            Files.move(Paths.get(MAIN_DATA_BACKUP), Paths.get(MAIN_DATA), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    /**
     * Persists the current sender timestamp data to file.
     * Helps track the last update time for each content server.
     */
    public void saveSenderData() {
        try {
            String jsonSenderData = JsonHandling.convertJSON(new ConcurrentHashMap<>(senderTimestamp));
            Files.write(Paths.get(SENDER_FILE_PATH_BACKUP), jsonSenderData.getBytes());
            Files.move(Paths.get(SENDER_FILE_PATH_BACKUP), Paths.get(SENDER_FILE_PATH), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    /**
     * Updates the last timestamp for a given sender (content server).
     * Used to track active content servers and manage data expiration.
     * @param key The identifier for the content server.
     * @param value The timestamp of the last update from this server.
     */
    public void saveTime(String key, long value) {
        System.out.println("Saving timestampe: " + key + " " + value);
        this.senderTimestamp.put(key, value);
    }

    /**
     * Retrieves all weather data stored in the database.
     * @return A map of station IDs to their respective weather data lists.
     */
    public Map<String, LinkedList<WeatherFormat>> getWeatherData() {
        return this.weatherData;
    }

    /**
     * Gets the last update timestamp for a specific sender.
     * @param k The identifier of the sender (content server).
     * @return The timestamp of the last update from the specified sender.
     */
    public Long getSenderTimestamp(String k) {
        return this.senderTimestamp.get(k);
    }

    /**
     * Retrieves the ID of the most recently updated weather station.
     * @return The station ID of the last updated weather data.
     */
    public String getStationID() {
        return this.latestStationID;
    }

    /**
    * Retrieves weather data for a specific station.
    * @param key The station ID to retrieve data for.
    * @return A LinkedList of WeatherFormat objects for the specified station.
    */
    public LinkedList<WeatherFormat> getWeatherData(String key) {
        return this.weatherData.get(key);
    }

    /**
     * Periodically updates the database by removing expired data.
     * Removes data from content servers that haven't sent updates within the expiration period.
     */
    public void updateData(){
        try {
            System.out.println("Updating data, will remove data...");
            long currtime = System.currentTimeMillis();
            ArrayList<String> sendersInvalid = new ArrayList<>();
            for (String key : this.senderTimestamp.keySet()) {
                long lastTime = this.senderTimestamp.get(key);
                if (currtime - lastTime > EXPIRE_SAVE) {
                    this.senderTimestamp.remove(key);
                    sendersInvalid.add(key);
                }
            }

            for (String stationID :this.weatherData.keySet()) {
                LinkedList<WeatherFormat> data = this.weatherData.get(stationID);
                data.removeIf(d -> sendersInvalid.contains(d.getSource()));
                if (data.isEmpty()) {
                    this.weatherData.remove(stationID);
                    
                }
            }
            this.saveWeatherData();
            this.saveSenderData();
        } catch (Exception e) {
           throw new RuntimeException("Error updating data: " + e.getMessage());
        }
    }

    /**
     * Clears all data from the database.
     * Used for resetting the database or in testing scenarios.
     */
    public void clear(){
        try {
            this.weatherData.clear();
            this.senderTimestamp.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
