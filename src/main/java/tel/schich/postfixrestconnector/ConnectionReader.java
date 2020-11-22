package tel.schich.postfixrestconnector;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface ConnectionReader extends Closeable {
    long read(ConnectionState state, SocketOps ops, ByteBuffer buffer) throws IOException;
}
