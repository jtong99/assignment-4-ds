import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

class JsonHandling_Test {

    @Test
    void testRead(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("data1_0.txt");
        Files.writeString(file, "Hello, World!");
        
        String content = JsonHandling.read(file.toString());
        assertEquals("Hello, World!\n", content);
    }

    @Test
    void testReadNonExistentFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("data1_0222.txt");
        
        assertThrows(Exception.class, () -> JsonHandling.read(file.toString()));
    }

    @Test
    void testConvertTextToJson() throws Exception {
        String input = "id:IDS60901\nname:Adelaide\nstate:SA\nair_temp:23.5";
        
        JsonObject result = JsonHandling.convertTextToJson(input);
        
        assertEquals("IDS60901", result.get("id").getAsString());
        assertEquals("Adelaide", result.get("name").getAsString());
        assertEquals("SA", result.get("state").getAsString());
        assertEquals(23.5, result.get("air_temp").getAsDouble(), 0.001);
    }

    @Test
    void testConvertTextToJsonInvalidInput() {
        String input = "invalid input";
        
        assertThrows(Exception.class, () -> JsonHandling.convertTextToJson(input));
    }

    @Test
    void testPrettier() {
        JsonObject input = new JsonObject();
        input.addProperty("name", "John");
        input.addProperty("age", 30);
        
        String result = JsonHandling.prettier(input);
        
        assertTrue(result.contains("{\n"));
        assertTrue(result.contains("  \"name\": \"John\",\n"));
        assertTrue(result.contains("  \"age\": 30\n"));
        assertTrue(result.contains("}"));
    }

    @Test
    void testConvertObject() {
        String jsonString = "{\"name\":\"John\",\"age\":30}";
        
        JsonObject result = JsonHandling.convertObject(jsonString, JsonObject.class);
        
        assertEquals("John", result.get("name").getAsString());
        assertEquals(30, result.get("age").getAsInt());
    }

    @Test
    void testConvertJSON() {
        JsonObject input = new JsonObject();
        input.addProperty("name", "John");
        input.addProperty("age", 30);
        
        String result = JsonHandling.convertJSON(input);
        
        assertTrue(result.contains("\"name\":\"John\""));
        assertTrue(result.contains("\"age\":30"));
    }

    @Test
    void testExtractJSONContent() {
        String input = "Some text before {\"name\":\"John\",\"age\":30} some text after";
        
        String result = JsonHandling.extractJSONContent(input);
        
        assertEquals("{\"name\":\"John\",\"age\":30}", result);
    }

    @Test
    void testParseJSONObject() throws JsonParseException {
        String input = "{\"name\":\"John\",\"age\":30}";
        
        JsonObject result = JsonHandling.parseJSONObject(input);
        
        assertEquals("John", result.get("name").getAsString());
        assertEquals(30, result.get("age").getAsInt());
    }

    @Test
    void testConvertJSONToText() throws Exception {
        JsonObject input = new JsonObject();
        input.addProperty("id", "IDS60901");
        input.addProperty("name", "Adelaide");
        input.addProperty("state", "SA");
        input.addProperty("air_temp", 23.5);
        
        String result = JsonHandling.convertJSONToText(input);
        
        assertTrue(result.contains("id: IDS60901\n"));
        assertTrue(result.contains("name: Adelaide\n"));
        assertTrue(result.contains("state: SA\n"));
        assertTrue(result.contains("air_temp: 23.5\n"));
    }
}