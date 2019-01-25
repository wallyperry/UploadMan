package ren.perry.uploadman;

public class FromDataPart {
    private String key;
    private String value;

    public FromDataPart(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
