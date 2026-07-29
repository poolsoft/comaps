package app.organicmaps.carlauncher.obd;

import java.io.IOException;

public interface UnderlyingTransport {
    String UNABLETOREAD = "UNABLETOREAD";
    String CONTEXTINACTIVE = "CONTEXTINACTIVE";
    String TIMEOUT = "TIMEOUT";

    void write(byte[] bytes) throws IOException;
    String read() throws IOException;
}
