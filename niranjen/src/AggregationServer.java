import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private static final int SERVER_CLEANUP_DELAY = 30000;
    private static final int POLLING_INTERVAL = 1;
    private static int port = DEFAULT_PORT;
    private static final LamportClock lamportClock = new LamportClock();
    private static final File contentDataFile = new File("Datafiles/mainDatafile.data");

    /**
     * Main method that starts the Aggregation Server. The port can be specified as a command-line argument.
     * If no port is specified or the port is invalid, the default port (4567) is used.
     * 
     * @param args Optional command-line arguments for the server, with the first argument as the port number.
     */
    public static void main(String[] args) {
        // Check and process port entered as args
        setPort(args);
        startServer();
    }

    private static void setPort(String[] args) {
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number. Using default port: " + DEFAULT_PORT + "!");
                port = DEFAULT_PORT;
            }
        }
    }

      /**
     * Starts the Aggregation Server, setting up the data file and handling client connections.
     * The server listens for incoming client connections, processes their requests in separate threads, 
     * and schedules a periodic task to remove outdated data from the data file.
     */
    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Create folder and file if not exists
            setupDataFiles();
            // update main file from existing temp files in case of server crash
            updateDataFileFromTempFiles();
            System.out.println("Aggregation Server started on port:" + port);
            // Starting service to cleanup data file if last update is older than 30s
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    synchronized (contentDataFile) {
                        try {
                            // Read data from data file
                            List<ContentData> contentList = new ArrayList<>();
                            FileUtils.readDataFile(contentDataFile, contentList);
                            // Get ids for content server with expired data
                            ListIterator<ContentData> iterator = contentList.listIterator(contentList.size());
                            List<String> idsToRemove = new ArrayList<>();
                            while (iterator.hasPrevious()) {
                                ContentData content = iterator.previous();
                                long lastModifiedTime = content.getLastUpdateTime();
                                if ((System.currentTimeMillis() - lastModifiedTime) > SERVER_CLEANUP_DELAY) {
                                    System.out.println("Removing outdated data received from content server id: " + content.getJson().get("id"));
                                    idsToRemove.add(content.getJson().get("id"));
                                }
                            }
                            // Delete expired data from data file
                            for (String id : idsToRemove) {
                                deleteFromList(contentList, id);
                            }
                            if (idsToRemove.size() > 0) {
                                System.out.println("Updating file after deletion of outdated data");
                                FileUtils.writeListToFile(contentDataFile, contentList);
                                System.out.println("Updated");
                            }
                        } catch (Exception e) {
                            System.out.println("Error Occured while checking for outdated data!");
                            e.printStackTrace();
                        }
                    }
                }
            };
            executorService.scheduleAtFixedRate(task, 0, POLLING_INTERVAL, TimeUnit.SECONDS);
            // Start new thread for incoming request
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new AggregationServerUtils(clientSocket, lamportClock, contentDataFile).start();
                } catch (IOException e) {
                    System.out.println("Error occurred while accepting client connection!");
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("Error occurred while starting server!");
            e.printStackTrace();
        }
    }

    /**
     * Ensures that the necessary data files and directories exist.
     * Creates the 'DataFiles' directory and the 'mainDatafile.data' file if they do not already exist.
     */
    private static void setupDataFiles() {
        File dataFilesDir = new File("DataFiles");
        if (!dataFilesDir.exists()) {
            if (dataFilesDir.mkdir()) {
                System.out.println("Created 'DataFiles' directory");
            } else {
                System.out.println("Failed to create 'DataFiles' directory!");
                return;
            }
        } else {
            System.out.println("Folder 'DataFiles' exists");
        }
        if (!contentDataFile.exists()) {
            try {
                if (contentDataFile.createNewFile()) {
                    System.out.println("Created 'mainDatafile.data'");
                } else {
                    System.out.println("Failed to create 'mainDatafile.data'!");
                    return;
                }
            } catch (IOException e) {
                System.out.println("Error occurred while creating 'mainDatafile.data'!");
                e.printStackTrace();
                return;
            }
        } else {
            System.out.println("Storage file 'mainDatafile.data' exists");
        }
    }


    /**
     * Deletes the entry corresponding to the specified content server ID from the content list.
     * 
     * @param contentDataList The list of content data.
     * @param id The ID of the content server whose data should be removed.
     */
    private static void deleteFromList(List<ContentData> contentDataList, String id) {
        Iterator<ContentData> iterator = contentDataList.iterator();
        while (iterator.hasNext()) {
            ContentData content = iterator.next();
            Map<String, String> dataMap = content.getJson();
            if (id.equals(dataMap.get("id"))) {
                iterator.remove();
            }
        }
    }


    /**
     * Updates the main data file with information from temporary files left in the DataFiles directory.
     * Temporary files older than the cleanup delay are deleted, while more recent data is read and 
     * added to the main file. After processing, all temporary files are deleted.
     */
    private static void updateDataFileFromTempFiles() {
        try {
            // Read all the json files from Datafiles folder
            File folder = new File("DataFiles");
            FilenameFilter filenameFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".json");
                }
            };
            File[] tempFiles = folder.listFiles(filenameFilter);
            if (tempFiles == null || tempFiles.length == 0) {
                return;
            }
            // Delete files if it's older than 30 seconds. else add to list
            List<ContentData> contentList = new ArrayList<>();
            for (File tempFile : tempFiles) {
                if (System.currentTimeMillis() - tempFile.lastModified() > SERVER_CLEANUP_DELAY) {
                    System.out.println("Deleting '" + tempFile.getName() + "expired data");
                    tempFile.delete();
                } else {
                    System.out.println("Reading non expired data from " + tempFile.getName());
                    BufferedReader tempFeader = new BufferedReader(new FileReader(tempFile));
                    Map<String, String> json = JsonUtils.parseJSON(tempFeader.readLine());
                    tempFeader.close();
                    contentList.add(new ContentData(json, tempFile.lastModified()));
                    tempFile.delete();
                }
            }
            // Sort list
            contentList.sort(new Comparator<ContentData>() {
                @Override
                public int compare(ContentData first, ContentData second) {
                    return Long.compare(first.getLastUpdateTime(), second.getLastUpdateTime());
                }
            });
            // Write data to files
            synchronized (contentDataFile) {
                FileUtils.writeListToFile(contentDataFile, contentList);
            }
        } catch (Exception e) {
            System.out.println("Error occured while processing unwritten temp files!");
            e.printStackTrace();
        }
    }
}
