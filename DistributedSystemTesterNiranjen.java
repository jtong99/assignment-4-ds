import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.net.*;

public class DistributedSystemTesterNiranjen {
    // Configuration constants
    private static final int FAILURE_TEST_PORT = 4567;
    private static final int SCALABILITY_TEST_PORT = 4568;
    private static final int LAMPORT_TEST_PORT = 4569;
    private static final int SERVER_START_TIMEOUT = 5000; // 5 seconds
    private static final int PROCESS_CLEANUP_TIMEOUT = 5000; // 5 seconds
    private static final String TEST_DATA_FILE = "weather_data.txt";
    private static final String TEST_JSON = "{\n" +
        "\"id\": \"IDS60901\",\n" +
        "\"name\": \"Adelaide (West Terrace /  ngayirdapira)\",\n" +
        "\"state\": \"SA\",\n" +
        "\"time_zone\": \"CST\"\n" +
    "}";
    // Track running processes for cleanup
    private static final List<Process> runningProcesses = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService testExecutor = Executors.newCachedThreadPool();
    
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nCleaning up processes and resources...");
            cleanupAll();
        }));
    }
    
    // Test implementation enum
    private enum Implementation {
        // MINE("mine"),
        NIRANJEN("niranjen");
        // NUR("nur")
        
        private final String directory;
        
        Implementation(String directory) {
            this.directory = directory;
        }
        
        public String getDirectory() {
            return directory;
        }
    }
    
    // Test result class
    private static class TestResult {
        String implementation;
        int successfulRequests;
        int failedRequests;
        long averageResponseTime;
        boolean lamportClockCorrect;
        boolean dataConsistent;
        List<Long> responseTimes;
        Map<String, Object> additionalMetrics;
        List<String> errors;
        
        public TestResult(String implementation) {
            this.implementation = implementation;
            this.responseTimes = new ArrayList<>();
            this.additionalMetrics = new HashMap<>();
            this.errors = new ArrayList<>();
            this.successfulRequests = 0;
            this.failedRequests = 0;
        }
        
        public void calculateAverageResponseTime() {
            if (!responseTimes.isEmpty()) {
                this.averageResponseTime = (long) responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            }
        }
        
        public void addError(String error) {
            this.errors.add(error);
            System.err.println("Error in " + implementation + ": " + error);
        }
    }
    
    // Utility methods for process management
    private static void cleanupAll() {
        testExecutor.shutdownNow();
        cleanupProcesses();
    }
    
    private static void cleanupProcesses() {
        for (Process process : runningProcesses) {
            try {
                if (process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(PROCESS_CLEANUP_TIMEOUT, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error cleaning up process: " + e.getMessage());
            }
        }
        runningProcesses.clear();
    }
    
    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    private static Integer extractLamportClock(Process process) throws IOException {
        // Read both standard output and error streams
        try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            
            String line;
            // Check standard output
            while ((line = stdOut.readLine()) != null) {
                if (line.contains("Lamport-Time:")) {
                    return Integer.parseInt(line.split("Lamport-Time:")[1].trim());
                }
                if (line.contains("Lamport clock after PUT request:")) {
                    return Integer.parseInt(line.split("Lamport clock after PUT request:")[1].trim());
                }
            }
            
            // Check error output
            while ((line = stdErr.readLine()) != null) {
                System.err.println("Error stream: " + line);
            }
            
            return null;
        }
    }

    private static void waitForPort(int port, int timeoutMillis) throws Exception {
        long startTime = System.currentTimeMillis();
        while (!isPortAvailable(port)) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                throw new TimeoutException("Port " + port + " did not become available");
            }
            Thread.sleep(100);
        }
    }
    
    // Process creation methods
    private static Process startServer(String directory, int port) throws Exception {
        waitForPort(port, SERVER_START_TIMEOUT);
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", "src", "AggregationServer", 
            String.valueOf(port))
            .directory(new File(directory));
        pb.inheritIO();
        Process process = pb.start();
        runningProcesses.add(process);
        
        // Wait for server to start
        Thread.sleep(2000);
        return process;
    }
    
    private static Process startContentServer(String directory, String dataFile, int port) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", "src", "ContentServer",
            "localhost:" + port, "src/" + dataFile)
            .directory(new File(directory));
        pb.inheritIO();
        Process process = pb.start();
        runningProcesses.add(process);
        return process;
    }
    
    // Test implementation methods
    private static TestResult testFailureHandling(Implementation impl) {
        TestResult result = new TestResult(impl.name());
        String directory = impl.getDirectory();
        
        try {
            cleanupProcesses();
            System.out.println("\nTesting Failure Handling for " + impl.name());
            
            // Test 1: Server Crash Recovery
            Process server = startServer(directory, FAILURE_TEST_PORT);
            Process contentServer = startContentServer(directory, TEST_DATA_FILE, FAILURE_TEST_PORT);
            System.out.println("Start server and content server");
            Thread.sleep(2000);
            
            // Force crash server
            server.destroy();
            System.out.println("server destroyed");
            runningProcesses.remove(server);
            System.out.println("server removed from runningProcesses");
            Thread.sleep(1000);
            
            // Restart and verify data
            server = startServer(directory, FAILURE_TEST_PORT);
            System.out.println("server restarted");
            boolean dataRecovered = verifyDataRecovery(directory, FAILURE_TEST_PORT);
            System.out.println("data recovered");
            result.dataConsistent = dataRecovered;
            if (dataRecovered) result.successfulRequests++; else result.failedRequests++;
            System.out.println("Test recovery success: " + dataRecovered);
            // Test 2: Content Server Disconnect
            contentServer.destroy();
            runningProcesses.remove(contentServer);
            Thread.sleep(31000); // Wait for expiry
            System.out.println("Test data recovery after content server disconnect");
            boolean dataExpired = !verifyDataRecovery(directory, FAILURE_TEST_PORT);
            if (dataExpired) result.successfulRequests++; else result.failedRequests++;
            
        } catch (Exception e) {
            result.addError("Failure handling test error: " + e.getMessage());
            System.out.println("Error in failure handling test: " + e.getMessage());
        } finally {
            cleanupProcesses();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        
        return result;
    }
    
    private static TestResult testScalability(Implementation impl) {
        TestResult result = new TestResult(impl.name());
        String directory = impl.getDirectory();
        
        try {
            cleanupProcesses();
            System.out.println("\nTesting Scalability for " + impl.name());
            
            Process server = startServer(directory, SCALABILITY_TEST_PORT);
            Process contentServer = startContentServer(directory, TEST_DATA_FILE, SCALABILITY_TEST_PORT);
            Thread.sleep(2000);
            
            // Concurrent client test
            int numClients = 100;
            CountDownLatch latch = new CountDownLatch(numClients);
            System.out.println("Starting concurrent client test with " + numClients + " clients...");
            
            for (int i = 0; i < numClients; i++) {
                testExecutor.submit(() -> {
                    try {
                        long startTime = System.currentTimeMillis();
                        boolean success = sendGetRequest(directory, SCALABILITY_TEST_PORT);
                        long endTime = System.currentTimeMillis();
                        
                        synchronized (result) {
                            if (success) {
                                result.successfulRequests++;
                                result.responseTimes.add(endTime - startTime);
                            } else {
                                result.failedRequests++;
                            }
                        }
                    } catch (Exception e) {
                        synchronized (result) {
                            result.addError("Client error: " + e.getMessage());
                            result.failedRequests++;
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            result.calculateAverageResponseTime();
            
        } catch (Exception e) {
            result.addError("Scalability test error: " + e.getMessage());
        } finally {
            cleanupProcesses();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        
        return result;
    }

    private static boolean verifyPutLamportClocks(String directory, int port) throws Exception {
        // Create two PUT requests and verify their Lamport clock values
        Socket socket1 = new Socket("localhost", port);
        Socket socket2 = new Socket("localhost", port);
        
        // Send first PUT
        PrintWriter out1 = new PrintWriter(socket1.getOutputStream(), true);
        out1.println("PUT /weather.json HTTP/1.1");
        out1.println("Content-Type: application/json");
        out1.println("Lamport-Clock: 1");
        out1.println();
        out1.println("{\"test\":\"data1\"}");
        
        // Send second PUT
        PrintWriter out2 = new PrintWriter(socket2.getOutputStream(), true);
        out2.println("PUT /weather.json HTTP/1.1");
        out2.println("Content-Type: application/json");
        out2.println("Lamport-Clock: 2");
        out2.println();
        out2.println("{\"test\":\"data2\"}");
        
        // Read responses and verify clock values
        BufferedReader in1 = new BufferedReader(new InputStreamReader(socket1.getInputStream()));
        BufferedReader in2 = new BufferedReader(new InputStreamReader(socket2.getInputStream()));
        
        Integer clock1 = null;
        Integer clock2 = null;
        
        String line;
        while ((line = in1.readLine()) != null) {
            if (line.contains("Lamport-Time:") || line.contains("Lamport clock after PUT request:")) {
                clock1 = Integer.parseInt(line.split(":")[1].trim());
                break;
            }
        }
        
        while ((line = in2.readLine()) != null) {
            if (line.contains("Lamport-Time:") || line.contains("Lamport clock after PUT request:")) {
                clock2 = Integer.parseInt(line.split(":")[1].trim());
                break;
            }
        }
        
        socket1.close();
        socket2.close();
        
        return clock1 != null && clock2 != null && clock2 > clock1;
    }
    
    private static TestResult testLamportClocks(Implementation impl) {
        TestResult result = new TestResult(impl.name());
        String directory = impl.getDirectory();
        
        try {
            cleanupProcesses();
            System.out.println("\nTesting Lamport Clocks for " + impl.name());
            
            // Start server
            Process server = startServer(directory, LAMPORT_TEST_PORT);
            Thread.sleep(2000);
            
            // Test using direct socket connections
            System.out.println("Testing Lamport clock synchronization...");
            
            // Send first PUT request with Lamport clock
            Integer clock1 = sendPutRequest(LAMPORT_TEST_PORT, 1);
            Thread.sleep(1000);
            
            // Send second PUT request with incremented clock
            Integer clock2 = sendPutRequest(LAMPORT_TEST_PORT, 2);
            
            System.out.println("First PUT Lamport clock: " + clock1);
            System.out.println("Second PUT Lamport clock: " + clock2);
            
            if (clock1 != null && clock2 != null) {
                result.lamportClockCorrect = (clock2 > clock1);
                if (result.lamportClockCorrect) {
                    result.successfulRequests++;
                    System.out.println("Lamport clock test passed: " + clock1 + " -> " + clock2);
                } else {
                    result.failedRequests++;
                    result.addError("Clock values not increasing: " + clock1 + " -> " + clock2);
                }
            } else {
                result.addError("Could not retrieve Lamport clock values");
                result.failedRequests++;
            }
            
        } catch (Exception e) {
            result.addError("Lamport clock test error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanupProcesses();
        }
        
        return result;
    }
    private static Integer sendPutRequest(int port, int clockValue) {
        try (Socket socket = new Socket("localhost", port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send PUT request with explicit Lamport clock header
            out.println("PUT /weather.json HTTP/1.1");
            out.println("Host: localhost:" + port);
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + TEST_JSON.length());
            out.println("Lamport-Time: " + clockValue);
            out.println();
            out.println(TEST_JSON);
            out.flush();
            
            // Read response and extract Lamport clock
            String line;
            Integer responseClock = null;
            while ((line = in.readLine()) != null) {
                System.out.println("Server response: " + line);
                if (line.contains("Lamport-Time:")) {
                    responseClock = Integer.parseInt(line.split("Lamport-Time:")[1].trim());
                }
                if (line.contains("Lamport clock after PUT request:")) {
                    responseClock = Integer.parseInt(line.split("Lamport clock after PUT request:")[1].trim());
                }
                if (line.isEmpty()) break;
            }
            
            return responseClock;
            
        } catch (Exception e) {
            System.err.println("Error sending PUT request: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static void logSocketCommunication(String message, String content) {
        System.out.println("=== " + message + " ===");
        System.out.println(content);
        System.out.println("==================");
    }

    // Helper method to read process output streams
    private static void drainProcessOutput(Process process) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Process output: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("Process error: " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    // Helper methods
    private static boolean sendGetRequest(String directory, int port) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", "src", "GETClient",
            "localhost:" + port)
            .directory(new File(directory));
        Process process = pb.start();
        runningProcesses.add(process);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            System.out.println("Output: " + output);
        }
        return process.waitFor(5, TimeUnit.SECONDS);
    }

    private static Integer extractLamportClockFromServer(String directory, int port) {
        try {
            // Create socket connection directly
            Socket socket = new Socket("localhost", port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send GET request with headers
            out.println("GET /weather.json HTTP/1.1");
            out.println("Host: localhost:" + port);
            out.println("User-Agent: AggregationClient/1.0");
            out.println("Accept: application/json");
            out.println("Lamport-Clock: 0"); // Send initial clock
            out.println(); // Empty line to end headers
            
            // Read response and look for Lamport clock in headers or content
            String line;
            Integer clockValue = null;
            while ((line = in.readLine()) != null) {
                // Try to find Lamport clock in different formats
                if (line.contains("Lamport-Time:")) {
                    clockValue = Integer.parseInt(line.split("Lamport-Time:")[1].trim());
                    break;
                }
                if (line.contains("Lamport-Clock:")) {
                    clockValue = Integer.parseInt(line.split("Lamport-Clock:")[1].trim());
                    break;
                }
                if (line.contains("lamport-time:")) {
                    clockValue = Integer.parseInt(line.split("lamport-time:")[1].trim());
                    break;
                }
            }
            
            socket.close();
            return clockValue;
            
        } catch (Exception e) {
            System.err.println("Error extracting Lamport clock: " + e.getMessage());
            return null;
        }
    }
    
    private static Future<Integer> sendAsyncGetRequest(String directory, int port) {
        return testExecutor.submit(() -> extractLamportClockFromServer(directory, port));
    }
    
    private static boolean verifyDataRecovery(String directory, int port) {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", "src", "GETClient",
                "localhost:" + port)
                .directory(new File(directory));
            Process process = pb.start();
            runningProcesses.add(process);
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.lines().collect(Collectors.joining("\n"));
                return output.contains("air_temp") && !output.contains("Error");
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void generateReport(Map<Implementation, List<TestResult>> allResults) {
        System.out.println("\n=================================================");
        System.out.println("           Distributed System Test Report          ");
        System.out.println("=================================================\n");
        
        for (Implementation impl : allResults.keySet()) {
            List<TestResult> results = allResults.get(impl);
            if (results == null || results.isEmpty()) continue;
            
            System.out.println("Implementation: " + impl.name());
            System.out.println("-------------------------------------------------");
            
            // Failure Handling Results
            TestResult failureResult = results.get(0);
            System.out.println("\n1. Failure Handling");
            System.out.println("   - Recovery success rate: " + 
                (failureResult.successfulRequests * 100.0 / 
                (failureResult.successfulRequests + failureResult.failedRequests)) + "%");
            System.out.println("   - Data persistence: " + 
                (failureResult.dataConsistent ? "Passed" : "Failed"));
            if (!failureResult.errors.isEmpty()) {
                System.out.println("   - Errors encountered:");
                failureResult.errors.forEach(error -> 
                    System.out.println("     * " + error));
            }
            
            // Scalability Results
            TestResult scalabilityResult = results.get(1);
            System.out.println("\n2. Scalability");
            System.out.println("   - Successful requests: " + scalabilityResult.successfulRequests);
            System.out.println("   - Failed requests: " + scalabilityResult.failedRequests);
            System.out.println("   - Average response time: " + 
                scalabilityResult.averageResponseTime + "ms");
            if (!scalabilityResult.errors.isEmpty()) {
                System.out.println("   - Errors encountered:");
                scalabilityResult.errors.forEach(error -> 
                    System.out.println("     * " + error));
            }
            
            // Lamport Clock Results
            TestResult lamportResult = results.get(2);
            System.out.println("\n3. Lamport Clock Implementation");
            System.out.println("   - Clock ordering correct: " + 
                (lamportResult.lamportClockCorrect ? "Yes" : "No"));
            if (!lamportResult.errors.isEmpty()) {
                System.out.println("   - Errors encountered:");
                lamportResult.errors.forEach(error -> 
                    System.out.println("     * " + error));
            }
            
            System.out.println("\n-------------------------------------------------\n");
        }
    }
    
    public static void main(String[] args) {
        Map<Implementation, List<TestResult>> allResults = new HashMap<>();
        
        try {
            for (Implementation impl : Implementation.values()) {
                System.out.println("\nTesting " + impl.name() + " implementation...");
                List<TestResult> results = new ArrayList<>();
                
                results.add(testFailureHandling(impl));
                results.add(testScalability(impl));
                results.add(testLamportClocks(impl));
                
                allResults.put(impl, results);
            }
            
            generateReport(allResults);
            
        } catch (Exception e){
            System.err.println("Error running tests: " + e.getMessage());
        }
    }
}