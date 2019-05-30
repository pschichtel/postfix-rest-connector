package tel.schich.postfixrestconnector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface ConnectionState {
    long read(SocketChannel ch, ByteBuffer buffer) throws IOException;
}
