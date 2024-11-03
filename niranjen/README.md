# DS_Assignment_2

Dev Notes:

Code was written in visual studio code and junit 4.12 was used for automation testing

How It Works

Aggregation Server: 

The server accepts PUT requests from content servers to store their weather data. When a client sends a GET request, the server retrieves the relevant data and returns it in JSON format.

The aggregation server maintains a limit of 20 records for weather data. When new data is received and the limit is reached, the oldest record will be removed to make space for the new entry.

The aggregation server implements a data cleaning mechanism. If a content server does not provide an update within 30 seconds, any records associated with that content server will be removed from the server's storage.



Content Server: 

The ContentServerUtils class continuously checks for changes in a specified data file. When a change is detected, it sends the updated weather data to the aggregation server using a PUT request. If the server is unavailable, it will retry connecting and sending the data at regular intervals until successful.


GETClient:

The GETClient class sends a GET request to the aggregation server to retrieve weather data. It constructs the request with optional parameters and reads the response to print the weather data in a specified order.

If the client fails to connect to the server, it will automatically retry every 10 seconds until a connection is established.


Acknowledgments

I would like to acknowledge the use of generative AI (ChatGPT) in modifying and improving the data cleaning functions,  server recovery functions and making my own JSON parsers (JsonUtils.java) for this assignment. The assistance provided helped in enhancing the overall functionality and efficiency of the code.



FeedBack for Draft


- No testing seems to be implemented at this stage.
- Please ensure thread safety when handling multiple requests at once. Consider using thread synchronization mechanisms like locks or concurrent data structures like ConcurrentHashMap


Fix based on Feedback

Added lock mechanism for handling put and get requests
Written junit test suite which runs multiple tests endtoend (which contains functionality of aggregation server), Synchronisationtest for server and retryConnection test for ContentServer and GETClient