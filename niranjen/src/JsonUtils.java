import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * The JsonUtils class provides utility methods for parsing and converting data
 * between JSON format and text files. It includes methods to parse a file into JSON,
 * parse a JSON string into a map, and convert a map back into a JSON string.
 */
public class JsonUtils {
    
    
    /**
     * Reads the specified text file and converts its content into a JSON string format.
     * The file is expected to contain key-value pairs separated by a colon.
     *
     * @param file The path to the file to be parsed into JSON.
     * @return A JSON-formatted string representation of the file content.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    public static String parseFileToJson(String file) throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(file));
        StringBuilder jsonData = new StringBuilder();
        jsonData.append("{\n");
        String line;
        boolean first = true;
        while ((line = fileReader.readLine()) != null) {
            if (!first) {
                jsonData.append(",\n");
            }
            first = false;
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                jsonData.append(String.format("\"%s\": \"%s\"", key, value));
            }
        }
        jsonData.append("\n}");
        fileReader.close();
        return jsonData.toString();
    }

     /**
     * Parses a JSON-formatted string and converts it into a map of key-value pairs.
     * This method validates the JSON structure and handles string values enclosed in quotes.
     *
     * @param jsonString The JSON string to be parsed.
     * @return A map containing the parsed key-value pairs from the JSON string.
     * @throws Exception If the JSON string is not properly formatted.
     */
    public static Map<String, String> parseJSON(String jsonString) throws Exception {
        jsonString = jsonString.trim();
        if (!jsonString.startsWith("{") || !jsonString.endsWith("}")) {
            //return null;
            throw new Exception("Invalid JSON format: Missing curly braces.");
        }
        Map<String, String> jsonMap = new HashMap<>();
        jsonString = jsonString.substring(1, jsonString.length() - 1).trim();
        String[] keyValuePairs = jsonString.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : keyValuePairs) {
            String[] keyValue = pair.split(":(?=([^\"]*\"[^\"]*\")*[^\"]*$)", 2);
            if (keyValue.length != 2) {
                //return null;
                throw new Exception("Invalid JSON format: Malformed key-value pair.");
            }
        
                String key = parseString(keyValue[0].trim());
                String value = parseString(keyValue[1].trim());
                jsonMap.put(key, value);
            
        }
        return jsonMap;
    }

    /**
     * Parses a string value by removing the enclosing double quotes.
     *
     * @param value The string value to be parsed.
     * @return The unquoted string value.
     * @throws Exception If the value is not enclosed in double quotes.
     */
    private static String parseString(String value) throws Exception {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        throw new Exception("Invalid JSON format: Expected string value.");
    }

    /**
     * Converts a map of key-value pairs into a JSON-formatted string.
     * The keys and string values are enclosed in double quotes.
     *
     * @param map The map containing key-value pairs to be converted into a JSON string.
     * @return A JSON-formatted string representation of the map.
     */
    public static String stringifyJson(Map<String, String> map) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        for (Map.Entry<String, String> entry : map.entrySet()) {
            jsonBuilder.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                jsonBuilder.append("\"").append(entry.getValue()).append("\"");
            } else {
                jsonBuilder.append(entry.getValue());
            }
            jsonBuilder.append(",");
        }
        jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }
}