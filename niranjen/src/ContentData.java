import java.util.Map;

public class ContentData {
    private Map<String, String> json;
    private long lastUpdateTime;

    public ContentData(Map<String, String> json, long lastUpdateTime) {
        this.json = json;
        this.lastUpdateTime = lastUpdateTime;
    }

    public Map<String, String> getJson() {
        return json;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public String toString() {
        return "{\"Data\": \"" + JsonUtils.stringifyJson(json) + "\", \"LastUpdateTime\": \"" + lastUpdateTime + "\"}";
    }
}