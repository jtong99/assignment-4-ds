import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.net.*;

public class DistributedSystemTesterNur {
    // Configuration constants
    private static final int FAILURE_TEST_PORT = 4567;
    private static final int SCALABILITY_TEST_PORT = 4568;
    private static final int LAMPORT_TEST_PORT = 4570;
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
        NUR("nur");
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
                if (line.contains("Lamport-Clock =")) {
                    return Integer.parseInt(line.split("Lamport-Clock =")[1].trim());
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
        final Process process = pb.start();
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
        Process server = null;
        Process contentServer = null;
        
        try {
            cleanupProcesses();
            System.out.println("\nTesting Failure Handling for " + impl.name());
            
            // TEST 1: Server Crash Recovery
            System.out.println("Starting Test 1: Server Crash Recovery");
            server = startServer(directory, FAILURE_TEST_PORT);
            contentServer = startContentServer(directory, TEST_DATA_FILE, FAILURE_TEST_PORT);
            System.out.println("Server and content server started");
            
            // Wait for initial data to be stored
            Thread.sleep(2000);
            
            // Verify initial data
            boolean initialDataPresent = verifyDataRecovery(directory, FAILURE_TEST_PORT);
            System.out.println("Initial data verification: " + (initialDataPresent ? "Success" : "Failed"));
            
            // Force crash server
            server.destroy();
            System.out.println("Server crashed");
            runningProcesses.remove(server);
            Thread.sleep(1000);
            
            // Restart and verify data persistence
            server = startServer(directory, FAILURE_TEST_PORT);
            System.out.println("Server restarted");
            Thread.sleep(2000);
            
            boolean dataRecovered = verifyDataRecovery(directory, FAILURE_TEST_PORT);
            System.out.println("Data recovery verification: " + (dataRecovered ? "Success" : "Failed"));
            result.dataConsistent = dataRecovered;
            if (dataRecovered) {
                result.successfulRequests++;
                System.out.println("Test 1 (Crash Recovery): Passed");
            } else {
                result.failedRequests++;
                System.out.println("Test 1 (Crash Recovery): Failed - Data not recovered after crash");
            }
    
            // TEST 2: Content Server Disconnect
            System.out.println("\nStarting Test 2: Content Server Disconnect");
            contentServer.destroy();
            runningProcesses.remove(contentServer);
            System.out.println("Content server disconnected");
            
            // Wait for expiry (30 seconds + buffer)
            System.out.println("Waiting 31 seconds for data expiry...");
            Thread.sleep(31000);
            
            // Verify data has been removed
            boolean dataExpired = !verifyDataRecovery(directory, FAILURE_TEST_PORT);
            if (dataExpired) {
                result.successfulRequests++;
                System.out.println("Test 2 (Data Expiry): Passed - Expired data properly removed");
            } else {
                result.failedRequests++;
                System.out.println("Test 2 (Data Expiry): Failed - Expired data still present");
            }
            
        } catch (Exception e) {
            result.addError("Failure handling test error: " + e.getMessage());
            System.err.println("Error in failure handling test: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (server != null) {
                server.destroyForcibly();
                runningProcesses.remove(server);
            }
            if (contentServer != null) {
                contentServer.destroyForcibly();
                runningProcesses.remove(contentServer);
            }
            cleanupProcesses();
        }
        
        System.out.println("\nFailure Handling Test Summary:");
        System.out.println("Total tests: 2");
        System.out.println("Successful tests: " + result.successfulRequests);
        System.out.println("Failed tests: " + result.failedRequests);
        System.out.println("Success rate: " + (result.successfulRequests * 100.0 / 2) + "%");
        
        return result;
    }
    
    // Helper method to verify data with retries
    private static boolean verifyDataRecovery(String directory, int port) {
        int maxRetries = 3;
        int retryDelay = 1000; // 1 second
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                System.out.println("Data verification attempt " + (i + 1) + "...");
                
                // Try socket connection first
                boolean socketResult = verifyDataWithSocket(port);
                if (socketResult) {
                    System.out.println("Data verified via socket connection");
                    return true;
                }
                
                // Try GET client as backup
                ProcessBuilder pb = new ProcessBuilder("java", "-cp", "src", "GETClient",
                    "localhost:" + port)
                    .directory(new File(directory));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                runningProcesses.add(process);
                
                CompletableFuture<String> output = new CompletableFuture<>();
                Thread outputThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                            if (line.contains("air_temp")) {
                                output.complete(sb.toString());
                                return;
                            }
                        }
                        output.complete(sb.toString());
                    } catch (IOException e) {
                        output.completeExceptionally(e);
                    }
                });
                outputThread.start();
                
                try {
                    String result = output.get(5, TimeUnit.SECONDS);
                    boolean success = result.contains("air_temp") && !result.contains("Error");
                    System.out.println("Data verification " + (success ? "successful" : "failed"));
                    if (success) return true;
                } catch (TimeoutException e) {
                    System.out.println("Verification timeout");
                } finally {
                    outputThread.interrupt();
                }
                
                if (i < maxRetries - 1) {
                    System.out.println("Retrying in " + retryDelay + "ms...");
                    Thread.sleep(retryDelay);
                }
                
            } catch (Exception e) {
                System.err.println("Error in verification attempt " + (i + 1) + ": " + e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        
        System.out.println("Data verification failed after " + maxRetries + " attempts");
        return false;
    }
    
    private static TestResult testScalability(Implementation impl) {
        TestResult result = new TestResult(impl.name());
        String directory = impl.getDirectory();
        Process server = null;
        Process contentServer = null;
        
        try {
            cleanupProcesses();
            System.out.println("\nTesting Scalability for " + impl.name());
            
            // Start server and content server
            server = startServer(directory, SCALABILITY_TEST_PORT);
            contentServer = startContentServer(directory, TEST_DATA_FILE, SCALABILITY_TEST_PORT);
            System.out.println("Server and content server started");
            
            // Wait for initial data to be available
            Thread.sleep(2000);
            
            // Verify initial data before starting concurrent test
            boolean initialDataAvailable = sendGETRequest(directory, SCALABILITY_TEST_PORT);
            if (!initialDataAvailable) {
                throw new RuntimeException("Initial data not available before scalability test");
            }
            System.out.println("Initial data verification successful");
            
            // Concurrent client test
            int numClients = 100;
            CountDownLatch latch = new CountDownLatch(numClients);
            Map<Integer, Long> responseTimes = new ConcurrentHashMap<>();
            AtomicInteger successCounter = new AtomicInteger(0);
            AtomicInteger failureCounter = new AtomicInteger(0);
            
            System.out.println("Starting concurrent test with " + numClients + " clients...");
            
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < numClients; i++) {
                final int clientId = i;
                Future<?> future = testExecutor.submit(() -> {
                    try {
                        long startTime = System.currentTimeMillis();
                        boolean success = sendGETRequest(directory, SCALABILITY_TEST_PORT);
                        long endTime = System.currentTimeMillis();
                        
                        if (success) {
                            successCounter.incrementAndGet();
                            responseTimes.put(clientId, endTime - startTime);
                            System.out.println("Client " + clientId + " succeeded in " + (endTime - startTime) + "ms");
                        } else {
                            failureCounter.incrementAndGet();
                            System.out.println("Client " + clientId + " failed");
                        }
                    } catch (Exception e) {
                        System.err.println("Client " + clientId + " error: " + e.getMessage());
                        failureCounter.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
                futures.add(future);
            }
            
            // Wait for all clients with timeout
            if (!latch.await(30, TimeUnit.SECONDS)) {
                System.err.println("Timeout waiting for clients to complete");
            }
            
            // Cancel any remaining tasks
            futures.forEach(f -> f.cancel(true));
            
            // Update result
            result.successfulRequests = successCounter.get();
            result.failedRequests = failureCounter.get();
            result.responseTimes.addAll(responseTimes.values());
            result.calculateAverageResponseTime();
            
            System.out.println("\nScalability Test Results:");
            System.out.println("Successful requests: " + result.successfulRequests);
            System.out.println("Failed requests: " + result.failedRequests);
            System.out.println("Average response time: " + result.averageResponseTime + "ms");
            
        } catch (Exception e) {
            result.addError("Scalability test error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (contentServer != null) {
                contentServer.destroyForcibly();
                runningProcesses.remove(contentServer);
            }
            if (server != null) {
                server.destroyForcibly();
                runningProcesses.remove(server);
            }
            cleanupProcesses();
        }
        
        return result;
    }

    private static TestResult testLamportClocks(Implementation impl) {
        TestResult result = new TestResult(impl.name());
        String directory = impl.getDirectory();
        Process server = null;
        Process contentServer1 = null;
        Process contentServer2 = null;
        
        try {
            cleanupProcesses();
            System.out.println("\nTesting Lamport Clocks for " + impl.name());
            
            // Start server
            server = startServer(directory, LAMPORT_TEST_PORT);
            System.out.println("Server started, waiting for initialization...");
            Thread.sleep(2000);
            
            // Start first content server and get its Lamport clock
            System.out.println("Starting first content server...");
            contentServer1 = startContentServer(directory, TEST_DATA_FILE, LAMPORT_TEST_PORT);
            Thread.sleep(1000); // Wait for content server to send data
            Integer clock1 = extractLamportClock(contentServer1);
            System.out.println("First content server Lamport clock: " + clock1);
            
            // Start second content server and get its Lamport clock
            System.out.println("Starting second content server...");
            contentServer2 = startContentServer(directory, TEST_DATA_FILE, LAMPORT_TEST_PORT);
            Thread.sleep(1000); // Wait for content server to send data
            Integer clock2 = extractLamportClock(contentServer2);
            System.out.println("Second content server Lamport clock: " + clock2);
            
            // Evaluate results
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
                String errorMsg = String.format("Could not retrieve Lamport clock values (clock1=%s, clock2=%s)", 
                    clock1, clock2);
                result.addError(errorMsg);
                result.failedRequests++;
            }
            
        } catch (Exception e) {
            result.addError("Lamport clock test error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up processes
            if (contentServer1 != null) {
                contentServer1.destroyForcibly();
                runningProcesses.remove(contentServer1);
            }
            if (contentServer2 != null) {
                contentServer2.destroyForcibly();
                runningProcesses.remove(contentServer2);
            }
            if (server != null) {
                server.destroyForcibly();
                runningProcesses.remove(server);
            }
            cleanupProcesses();
        }
        
        return result;
    }
    // Helper methods
    private static boolean sendGETRequest(String directory, int port) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "java", 
                "-cp", 
                "src", 
                "GETClient",
                "localhost:" + port)
                .directory(new File(directory));
            
            // Redirect error stream to output stream
            pb.redirectErrorStream(true);
            
            process = pb.start();
            runningProcesses.add(process);
            
            // Read the output with timeout
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                
                new Thread(() -> {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("air_temp")) {
                                future.complete(true);
                                return;
                            }
                        }
                        future.complete(false);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                }).start();
                
                return future.get(5, TimeUnit.SECONDS);
            }
            
        } catch (Exception e) {
            System.err.println("Error in GET request: " + e.getMessage());
            return false;
        } finally {
            if (process != null) {
                process.destroyForcibly();
                runningProcesses.remove(process);
            }
        }
    }
    
    
    // Alternative verification using direct socket connection
    private static boolean verifyDataWithSocket(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("GET /weather.json HTTP/1.1");
            out.println("Host: localhost:" + port);
            out.println("Accept: application/json");
            out.println();
            out.flush();
            
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("air_temp")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error verifying data: " + e.getMessage());
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