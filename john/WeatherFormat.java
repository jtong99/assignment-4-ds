import com.google.gson.JsonObject;

public class WeatherFormat implements Comparable<WeatherFormat> {
    private int lamport;
    private String source;
    private JsonObject data;

    public WeatherFormat(int lamport, String source, JsonObject data) {
        this.lamport = lamport;
        this.source = source;
        this.data = data;
    }

    public int getLamport() {
        return this.lamport;
    }
    public JsonObject getData() {
        return this.data;
    }
    @Override
    public int compareTo(WeatherFormat other) {
        return Integer.compare(this.lamport, other.lamport);
    }
    public String getSource() {
        return this.source;
    }
}
