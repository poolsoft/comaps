package app.organicmaps.carlauncher.obd;

public class OBDDataField<T> {
    private final T value;
    private final long timestamp;

    public static final OBDDataField<Object> NO_DATA = new OBDDataField<>(new Object());

    public OBDDataField(T value) {
        this.value = value;
        this.timestamp = System.currentTimeMillis();
    }

    public T getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
