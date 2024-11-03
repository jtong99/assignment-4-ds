import java.io.*;
import java.net.*;
import java.util.*;


/**
This class handles incoming client requests to the aggregation server for weather data storage.
It processes Socket PUT and GET requests and updates or retrieves weather data in JSON format.
The class also uses a Lamport Clock for managing logical time across distributed systems. */
public class AggregationServerUtils extends Thread {
    private Socket socket;
    private LamportClock lamportClock;
    private File contentFile;
    private static final Object lock = new Object();

    AggregationServerUtils(Socket socket, LamportClock lamportClock, File conFile) {
        this.socket = socket;
        this.lamportClock = lamportClock;
        this.contentFile = conFile;
    }

    /**
        Main execution method for handling client requests.
        It reads HTTP requests, updates the Lamport clock, and processes GET and PUT requests. */ 
    @Override
    public void run() {
        try {
            System.out.println("Connection Received..");
            InputStream inputStream = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            OutputStream outputStream = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(outputStream, true);
            HashMap<String, String> dataMap = new HashMap<>();
            String dataLine = reader.readLine();
            String[] requestParts = dataLine.split(" ");
            // Respond with 400 if method or endpoint is not available
            if (requestParts.length < 2) {
                writer.println("HTTP/1.1 400 Bad Request");
                writer.println();
                return;
            }
            String method = requestParts[0];
            String endpoint = requestParts[1];
            // Reading request header and body
            while ((dataLine = reader.readLine()) != null) {
                if (dataLine.isEmpty()) {
                    break;
                }
                if (dataLine.startsWith("{")) {
                    String jsonData = "{";
                    while (true) {
                        dataLine = reader.readLine();
                        if (dataLine == null || dataLine.isEmpty()) {
                            break;
                        }
                        if (dataLine.equals("}")) {
                            break;
                        }
                        jsonData += dataLine;
                    }
                    jsonData += "}";
                    dataMap.put("Data", jsonData);
                } else {                                                    //clear
                    String[] requestData = dataLine.split(": ");
                    if (requestData.length == 2) {
                        String key = requestData[0];
                        String value = requestData[1];
                        dataMap.put(key, value);
                    }
                }
            }
            // Read and update Lamport Time
            int recievedLamportTime = Integer.parseInt(dataMap.get("Lamport-Time"));
            lamportClock.update(recievedLamportTime);
            //Sending Lamport time after update
            writer.println("Lamport-Time: " + lamportClock.tick()); 
            synchronized (lock) {
                // Process request based on method and endpoint
                if ("PUT".equals(method) && "/weather.json".equals(endpoint.split("\\?")[0])) {
                    System.out.println("Handling PUT request..");
                    try {
                        Map<String, String> json = JsonUtils.parseJSON(dataMap.get("Data"));
                        String fileName = json.get("id") + ".json";
                        // Writing to temp .json file rather than mainDatafile.data
                        FileUtils.writeToTempFile(fileName, JsonUtils.stringifyJson(json));
                        // Writing to main file
                        short code = handlePUTRequest(fileName);
                        FileUtils.deleteTempFile(fileName);
                        switch (code) {
                            case 0:
                                // 201 if data is newly added from content server
                                writer.println("HTTP/1.1 201 Created");
                                break;
                            case 1:
                                // 200 if data already exists and added from content server
                                writer.println("HTTP/1.1 200 OK");
                                break;
                            default:
                                // 500 if error happens while processing
                                writer.println("HTTP/1.1 500 Internal Server Error");
                                break;
                        }
                    } catch (Exception e) {
                        System.out.println("Invalid Json!");
                        e.printStackTrace();
                        // 500 if request body (json is invalid)
                        writer.println("HTTP/1.1 500 Internal Server Error");
                    }
                } else if ("GET".equals(method) && "/weather.json".equals(endpoint.split("\\?")[0])) {
                    System.out.println("Handling GET request..");
                    String[] endpointAndQuery = endpoint.split("\\?");
                    Map<String, String> json = handleGETRequest(endpointAndQuery.length > 1 ? endpointAndQuery[1] : "");
                    if (json != null) {
                        // 200 and json if request is executed correctly
                        writer.println("HTTP/1.1 200 OK");
                        writer.println(JsonUtils.stringifyJson(json));
                    } else {
                        // 404 if data is empty
                        writer.println("HTTP/1.1 404 Resource not found");
                    }
                } else {
                    // 400 if method or endpoint is invalid
                    System.out.println("Request method or endpoint not found!");
                    writer.println("HTTP/1.1 400 Bad Request");
                }
                writer.println();
            } // lock released
        } catch (Exception e) {
            System.out.println("Error while reading data from client!");
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Failed to close socket!");
                e.printStackTrace();
            }
        }
    }

   /**
     * Handles the PUT request to store or update weather data in a temporary file.
     *
     * @param tempFile The name of the temporary file containing the JSON data.
     * @return A short value indicating the result: 0 for new data, 1 for updated data, and -1 for errors.
     */
    private short handlePUTRequest(String tempFile) {
        try {
            short code = 0;
            // Read data from temp file
            BufferedReader tempFeader = new BufferedReader(new FileReader("DataFiles/" + tempFile));
            Map<String, String> json = JsonUtils.parseJSON(tempFeader.readLine());
            tempFeader.close();
            synchronized (contentFile) {
                if (!contentFile.exists()) {
                    throw new Exception("Storage file 'mainDatafile.data' not found!!");
                }
                // Read existing data from data file
                List<ContentData> contentDataList = new ArrayList<>();
                FileUtils.readDataFile(contentFile, contentDataList);
                // Delete old entry if size of data is 20
                if (contentDataList.size() == 20) {
                    contentDataList.remove(0);
                }
                if (contentDataList.size() > 0) {
                    for (ContentData contentData : contentDataList) {
                        if (contentData.getJson().get("id").equals(json.get("id"))) {
                            code = 1;
                        }
                    }
                }
                // Write new data to data file
                contentDataList.add(new ContentData(json, System.currentTimeMillis()));
                FileUtils.writeListToFile(contentFile, contentDataList);
            }
            return code;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error processing PUT request!");
            return -1;
        }
    }

    /**
     * Handles the GET request to retrieve weather data from the storage file.
     *
     * @param queryString The query string from the GET request URL (optional).
     * @return A Map representing the JSON data for the weather station, or null if no data is found.
     */
    private Map<String, String> handleGETRequest(String queryString) {
        try {
            // Set station id if available
            String stationId = null;
            if (queryString.startsWith("stationId")) {
                stationId = queryString.split("=")[1];
            }
            synchronized (contentFile) {
                if (!contentFile.exists()) {
                    throw new Exception("Storage file 'mainDatafile.data' not found");
                }
                // Read data from data file
                List<ContentData> contentDataList = new ArrayList<>();
                FileUtils.readDataFile(contentFile, contentDataList);
                if (stationId == null) {
                    // Return latest data if station id is not available
                    if (!contentDataList.isEmpty()) {
                        return contentDataList.get(contentDataList.size() - 1).getJson();
                    } else {
                        return null;
                    }
                } else {
                    // Return latest data of a particular station if id is available
                    ListIterator<ContentData> iterator = contentDataList.listIterator(contentDataList.size());
                    Map<String, String> json = null;
                    while (iterator.hasPrevious()) {
                        ContentData content = iterator.previous();
                        if (content.getJson().get("id").equals(stationId)) {
                            json = content.getJson();
                            break;
                        }
                    }
                    return json;
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing GET request!");
            e.printStackTrace();
            return null;
        }
    }
}