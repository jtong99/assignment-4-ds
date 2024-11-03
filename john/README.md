# Assignment 2

## Student

- Name: Minh Duc Tong (John)
- ID: a1941699

## Overview

This project implements socket distributed weather data system consisting of multiple components that work together to collect, aggregate, and serve weather information. The system is designed to be fault-tolerant, maintain data consistency using Lamport clocks, and provide a RESTful API for data access.

## Files

1. MainAggregationServer.java: Acts as a load balancer and coordinator for multiple AggregationServers. Currently, I implemented 3 aggregation servers that will handle fault-tolerant, if 1 server is down, the MainAggregationServer will automatically switch the other one.
2. AggregationServer.java: Handles client requests, processes weather data, and manages data persistence.
3. ContentServer.java: Read weather data from text file and uploads weather data to the AggregationServer.
4. GETClient.java: Send requests to retrieve weather data from the AggregationServer.
5. DatabaseManagement.java: Manages data persistence and handles data expiration.
6. SocketServer.java: Custom implementation for socket-based communication
7. Lamport.java: Lamport logical clock that will be used for aggregation server, content server and GETClient.
8. JsonHandling.java: Utility class for JSON operations

## Data management

The system manages two primary types of data: weather data and sender data.

### Weather data `data/data.json`

- Weather data will be stored in `data.json`.
- Ensures that weather data survives server restarts or crashes.
- Allows the system to recover its state after unexpected shutdowns.
- Serves as a backup of the system's data.

```
{
  "IDS60901": [
    {
      "lamport": 1,
      "source": "e4e4323f-45b7-46d9-8472-e9e348d35b8d",
      "data": {
        "id": "IDS60901",
        "name": "Adelaide (West Terrace /  ngayirdapira)",
        "state": "SA",
        "time_zone": "CST",
        "lat": "40",
        "lon": "138.6",
        "local_date_time": "15/04:00pm",
        "local_date_time_full": "20230715160000",
        "air_temp": "13.3",
        "apparent_t": "9.5",
        "cloud": "Partly cloudy",
        "dewpt": "5.7",
        "press": "1023.9",
        "rel_hum": "60",
        "wind_dir": "S",
        "wind_spd_kmh": "15",
        "wind_spd_kt": "8"
      }
    }
  ]
}
```

### Sender data `data/sender.json`

- Sender data will be stored in `data.json`.
- When content server send request PUT, senderID will be stored in this file.
- It will be used to check latest data and remove it if it's old data.

```
{ "e4e4323f-45b7-46d9-8472-e9e348d35b8d": 1728013894190 }
```

## Lamport Clock

Distributed weather data system implements Lamport logical clocks to maintain a partial ordering of events across multiple distributed components.

### Lamport

It maintains an AtomicInteger to represent the current logical time.

Provides methods to increment the clock (tick()), adjust the clock based on received timestamps (adjust()), and retrieve the current time (getTime()).

### System Components

### AggregationServer

- Maintains a Lamport clock instance to timestamp all operations.
- Updates its clock on every client interaction (GET or PUT requests).
- Includes the current Lamport time in responses to clients, facilitating system-wide clock synchronization.
- If one server is down, the MainAggregationServer will redirect request to another active AS, in that case, the Lamport clock will be reseted, I implemented the function `ensureClockConsistency` to synchronize Lamport clock and update it.
- After 30s without updating content from Content Server, Aggregation Server will automatically remove data.

### ContentServer

- Initializes its own Lamport clock upon startup.
- Sends its current Lamport time with each PUT request to the AggregationServer.
- Adjusts its clock based on the AggregationServer's response, ensuring it stays synchronized with the server.
- Uses the Lamport time to version its weather data updates, allowing the server to order updates correctly.
- If Content Server cannot connect to Aggregation Server, it will retry upload data in 3 times.

### Client (GETClient)

- Maintains a Lamport clock to track the logical time of its operations.
- Sends its current Lamport time with each GET request.
- Adjusts its clock based on the AggregationServer's response.
- Uses the received Lamport time to understand the "age" of the received weather data in terms of logical time.
- If Client Server cannot connect to Aggregation Server, it will retry upload data in 3 times.

## Test Suite

My test suite is designed to verify the functionality and robustness of a distributed weather data aggregation system. It tests various scenarios including normal operation, server failures, and data expiration. The test suite uses multiple aggregation servers, content servers, and clients to simulate a realistic distributed environment. It included unit tests and integration tests.

### 1. Unit test

Mostly testing functions in main class.

- DatabaseManagement_Test
- AggregationServer_Test
- JsonHandling_Test
- ContentServer_Test
- GETClient_Test

### 2. Integration test

Test Case Descriptions:

- testMultipleContentServerUploads(): This test verifies that multiple content servers can successfully upload data to the aggregation system. It uploads data from two different content servers and then checks if clients can retrieve the correct data for each weather station.

- testReplicationServerFailover(): This test examines the system's fault tolerance. It uploads data, then shuts down one of the replication servers. It then verifies that the data is still accessible from the remaining servers, demonstrating the system's ability to handle server failures.

- testNormalCase(): This test simulates a more complex normal operation scenario. It involves multiple data uploads from two content servers and subsequent data retrievals by two clients. It checks if the system correctly handles multiple updates and if clients can retrieve the most recent data for different weather stations.

- test1ASServerDown(): Similar to the normal case, but it simulates a scenario where one aggregation server goes down after data has been uploaded. It verifies that the system continues to function correctly and serve accurate data even when one server is unavailable.

- testServerDown(): This test checks the system's behavior when all aggregation servers are down. It attempts to upload data and retrieve it when no servers are available, verifying how the system handles and reports complete service unavailability.

- testDataExpirationAfter30Seconds(): This test focuses on the data expiration policy. It uploads data, waits for more than 30 seconds, and then attempts to retrieve the data. It verifies that the system correctly removes data that hasn't been updated within the specified timeframe.

```
╷
├─ JUnit Jupiter ✔
│  ├─ DatabaseManagement_Test ✔
│  │  ├─ testData() ✔
│  │  └─ testGetTime() ✔
│  ├─ AggregationServer_Test ✔
│  │  ├─ testServerShutdown() ✔
│  │  ├─ testHandleGetRequestWithContent() ✔
│  │  ├─ testHandleGetRequestWithoutData() ✔
│  │  └─ testHandlePutRequest() ✔
│  ├─ JsonHandling_Test ✔
│  │  ├─ testReadNonExistentFile(Path) ✔
│  │  ├─ testConvertJSON() ✔
│  │  ├─ testConvertJSONToText() ✔
│  │  ├─ testConvertTextToJsonInvalidInput() ✔
│  │  ├─ testParseJSONObject() ✔
│  │  ├─ testRead(Path) ✔
│  │  ├─ testExtractJSONContent() ✔
│  │  ├─ testConvertObject() ✔
│  │  ├─ testPrettier() ✔
│  │  └─ testConvertTextToJson() ✔
│  ├─ ContentServer_Test ✔
│  │  ├─ testRetryUpload() ✔
│  │  ├─ testLoadWeatherData() ✔
│  │  ├─ testUploadData() ✔
│  │  ├─ testShutdown() ✔
│  │  └─ testIsLoadFileSuccess() ✔
│  ├─ TestIntegration_Test ✔
│  │  ├─ testNormalCase() ✔
│  │  ├─ testMultipleContentServerUploads() ✔
│  │  ├─ test1ASServerDown() ✔
│  │  ├─ testDataExpirationAfter30Seconds() 37027 ms ✔
│  │  ├─ testServerDown() 13036 ms ✔
│  │  └─ testReplicationServerFailover() ✔
│  └─ GETClient_Test ✔
│     ├─ testSendRequestNoContent() ✔
│     ├─ testGetServerInfo() ✔
│     ├─ testSendRequestServiceUnavailable() ✔
│     ├─ testSendRequest() ✔
│     └─ testGetServerInfoInvalid() ✔
└─ JUnit Vintage ✔

Test run finished after 69438 ms
[         8 containers found      ]
[         0 containers skipped    ]
[         8 containers started    ]
[         0 containers aborted    ]
[         8 containers successful ]
[         0 containers failed     ]
[        32 tests found           ]
[         0 tests skipped         ]
[        32 tests started         ]
[         0 tests aborted         ]
[        32 tests successful      ]
[         0 tests failed          ]
```

## Command

1. Compile all: `make all`

2. Run test cases: `make test`

3. Run main aggregation server: `make main`

4. Run content server: `make contentserver`

5. Run client: `make client`
