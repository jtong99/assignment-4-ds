import com.google.gson.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Type;
import java.util.Map.Entry;

public class JsonHandling {
    private static final Gson gson = new Gson();

    private JsonHandling() {}

    public static String read(String file) throws Exception {
        if (file == null) {
            throw new Exception("filePath is invalid.");
        }

        StringBuilder content = new StringBuilder();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new Exception("Error reading the file: " + e.getMessage());
        }

        return content.toString();
    }

    public static JsonObject convertTextToJson(String inputText) throws Exception {
        if (inputText == null) {
            throw new Exception("Null input.");
        }

        String[] lines = inputText.split("\n");
        Map<String, Object> dataMap = new LinkedHashMap<>();

        for (String line : lines) {
            String[] parts = line.split(":", 2);

            if (parts.length != 2) {
                throw new Exception("Invalid: " + line);
            }

            String key = parts[0].trim();
            String value = parts[1].trim();

            dataMap.put(key, value);
        }

        return gson.toJsonTree(dataMap).getAsJsonObject();
    }


    public static String prettier(JsonObject jsonObject) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(jsonObject);
    }

    public static <T> T convertObject(String jsonString, Type type) throws JsonSyntaxException {
        return gson.fromJson(jsonString, type);
    }
    public static <T> String convertJSON(T object) {
        return gson.toJson(object);
    }
    public static String extractJSONContent(String data) {
        int startIndex = data.indexOf("{");
        int endIndex = data.indexOf("}", startIndex);

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return data.substring(startIndex, endIndex + 1);
        } else {
            return null;
        }
    }
    public static JsonObject parseJSONObject(String jsonData) throws JsonParseException {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return null;
        }

        return gson.fromJson(jsonData, JsonObject.class);
    }

    public static String convertJSONToText(JsonObject jsonObject) throws Exception {
        if (jsonObject == null) {
            throw new Exception("Error: jsonObject is null.");
        }

        StringBuilder stringBuilder = new StringBuilder();

        for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonElement valueElement = entry.getValue();

            String valueStr;
            if (valueElement.isJsonPrimitive() && (valueElement.getAsJsonPrimitive().isNumber() || valueElement.getAsJsonPrimitive().isString())) {
                valueStr = valueElement.getAsString();
            } else {
                valueStr = gson.toJson(valueElement);
            }

            stringBuilder.append(key).append(": ").append(valueStr).append("\n");
        }

        return stringBuilder.toString();
    }
}
