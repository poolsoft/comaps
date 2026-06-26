package app.organicmaps.carlauncher.obd;

import java.util.Arrays;

public final class OBDResponse {
    private final int[] result;

    public static final OBDResponse OK = new OBDResponse(new int[]{1});
    public static final OBDResponse QUESTION_MARK = new OBDResponse(new int[]{0});
    public static final OBDResponse NO_DATA = new OBDResponse(new int[]{-2});
    public static final OBDResponse ERROR = new OBDResponse(new int[]{-1});
    public static final OBDResponse CONNECTION_FAILURE = new OBDResponse(new int[]{-4});
    public static final OBDResponse STOPPED = new OBDResponse(new int[]{-3});

    public OBDResponse(int[] result) {
        this.result = result;
    }

    public int[] getResult() {
        return result;
    }

    public boolean isValid() {
        return !equals(OK) && !equals(QUESTION_MARK) && !equals(NO_DATA) && !equals(ERROR) && !equals(STOPPED);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OBDResponse that = (OBDResponse) o;
        return Arrays.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(result);
    }
}
