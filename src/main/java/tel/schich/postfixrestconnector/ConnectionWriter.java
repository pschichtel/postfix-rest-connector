package tel.schich.postfixrestconnector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.UUID;

public final class ConnectionWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionWriter.class);

    private final UUID id;
    private final SelectionKey key;
    private final Continuation k;
    private final byte[] data;
    private int offset;
    private int length;

    public ConnectionWriter(UUID id, SelectionKey key, byte[] data, int offset, int length, Continuation k) {
        this.id = id;
        this.key = key;
        this.k = k;
        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    boolean run(SocketChannel ch, ByteBuffer buf) {
        try {
            buf.put(data, offset, length);
            buf.flip();

            final int bytesWritten = ch.write(buf);
            LOGGER.trace("{} - bytes written: {}", id, bytesWritten);
            this.offset += bytesWritten;
            this.length -= bytesWritten;
            if (this.length <= 0) {
                LOGGER.debug("{} - write operation complete", id);
                k.run(null);
                return true;
            }
            return false;
        } catch (IOException e) {
            LOGGER.error("{} - write operation failed!", id, e);
            k.run(e);
            return true;
        }
    }
}
