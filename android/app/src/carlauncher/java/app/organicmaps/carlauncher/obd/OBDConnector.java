package app.organicmaps.carlauncher.obd;

import android.util.Pair;
import okio.Sink;
import okio.Source;

public interface OBDConnector {
    Pair<Source, Sink> connect() throws Exception;
    void onConnectionSuccess();
    void onConnectionFailed();
    void disconnect();
}
