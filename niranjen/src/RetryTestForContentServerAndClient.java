import org.junit.Test;  
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;  
  
public class RetryTestForContentServerAndClient {  
  
 
  @Test  
  public void testRetrySendWeatherData_ConnectionFailure_ContentServer() {  
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); 
    PrintStream originalOut = System.out; 
    System.setOut(new PrintStream(outputStream));
    
    Thread thread = new Thread(new Runnable() {  
      @Override  
      public void run() {  
        ContentServer.main(new String[] { "localhost:8080", "src/weather_data.txt" });  
      }  
   });  
   thread.start();
    
    // wait for retry message to be printed
    try {  
        Thread.sleep(5000);  
     } catch (InterruptedException e) {  
        Thread.currentThread().interrupt();  
     }
     //capturing output
    String output = outputStream.toString();
     
    assertTrue(output.contains("Error: Failed to connect or send data! Retrying..."));  

    System.setOut(originalOut);
  }  
  
  @Test  
public void testRetrySendWeatherData_ConnectionFailure_GETClient() {  
   ByteArrayOutputStream outputStream = new ByteArrayOutputStream();  
   PrintStream originalOut = System.out;  
   System.setOut(new PrintStream(outputStream));  
  
   Thread thread = new Thread(new Runnable() {  
      @Override  
      public void run() {  
        GETClient.main(new String[] {"localhost:8080"});  
      }  
   });  
   thread.start();  
  
   // wait for retry message to be printed
   try {  
      Thread.sleep(5000);  
   } catch (InterruptedException e) {  
      Thread.currentThread().interrupt();  
   }  
  
   //capturing output
   String output = outputStream.toString();  
  
   assertTrue(output.contains("Error: Failed to connect!! Retrying..."));  
   System.setOut(originalOut);  
}
 
}
