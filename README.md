# Assignment 4

## Student

- Name: Minh Duc Tong (John)
- ID: a1941699

## Description

This test suite evaluates three different implementations of a distributed weather data aggregation system. The test files (DistributedSystemTesterJohn.java, DistributedSystemTesterNiranjen.java, and DistributedSystemTesterNur.java) assess the following key aspects:

### 1. Failure Handling Test

- Server crash recovery capabilities
- Data persistence verification after server restart
- Content server disconnection handling
- Data expiry mechanism (30-second timeout)
- Recovery success rate measurement

### 2. Scalability Test

- Concurrent request handling (100 simultaneous clients)
- Response time measurement
- Success/failure rate under load
- System performance metrics collection
- Resource management under stress

### 3. Lamport Clock Test

- Logical time ordering verification
- Clock synchronization between components
- Event ordering consistency
- Clock value propagation across system

## Command

Evaluate my implementation

```bash
java DistributedSystemTesterJohn
```

Evaluate Niranjen's implementation

```bash
java DistributedSystemTesterNiranjen
```

Evaluate Nur's implementation

```bash
java DistributedSystemTesterNur
```

## Result

My report

```
=================================================
           Distributed System Test Report
=================================================

Implementation: JOHN
-------------------------------------------------

1. Failure Handling
   - Recovery success rate: 100.0%
   - Data persistence: Passed

2. Scalability
   - Successful requests: 100
   - Failed requests: 0
   - Average response time: 12096ms

3. Lamport Clock Implementation
   - Clock ordering correct: Yes

-------------------------------------------------
```

Niranjen report

```
=================================================
           Distributed System Test Report
=================================================

Implementation: NIRANJEN
-------------------------------------------------

1. Failure Handling
   - Recovery success rate: 100.0%
   - Data persistence: Passed

2. Scalability
   - Successful requests: 100
   - Failed requests: 0
   - Average response time: 13511ms

3. Lamport Clock Implementation
   - Clock ordering correct: Yes

-------------------------------------------------
```

Nur report

```
=================================================
           Distributed System Test Report
=================================================

Implementation: NUR
-------------------------------------------------

1. Failure Handling
   - Recovery success rate: 50.0%
   - Data persistence: Passed

2. Scalability
   - Successful requests: 100
   - Failed requests: 0
   - Average response time: 2867ms

3. Lamport Clock Implementation
   - Clock ordering correct: No
   - Errors encountered:
     * Could not retrieve Lamport clock values (clock1=null, clock2=null)

-------------------------------------------------
```
