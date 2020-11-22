/*
 * Postfix REST Connector - A simple TCP server that can be used as tcp lookup for the Postfix mail server.
 * Copyright © 2018 Phillip Schichtel (phillip@schich.tel)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package tel.schich.postfixrestconnector;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.UUID;

public class ConnectionState implements Closeable, SocketOps {
    private final UUID id = UUID.randomUUID();
    private final SelectionKey key;
    private final SocketChannel channel;
    private final ConnectionReader reader;
    private ConnectionWriter writer;

    public ConnectionState(SelectionKey key, SocketChannel channel, ConnectionReader reader) {
        this.key = key;
        this.channel = channel;
        this.reader = reader;
    }

    public UUID getId() {
        return id;
    }

    public SelectionKey getKey() {
        return key;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public ConnectionReader getReader() {
        return reader;
    }

    public ConnectionWriter getWriter() {
        return writer;
    }

    public void clearWriter() {
        writer = null;
    }

    public long read(SocketOps ops, ByteBuffer buffer) throws IOException {
        return getReader().read(this, ops, buffer);
    }

    @Override
    public void submitOp(Op op, Continuation k) {
        final ConnectionWriter newWriter = op.apply(getKey(), getChannel(), getId(), k);
        if (newWriter != null) {
            if (writer == null) {
                writer = newWriter;
            } else {
                // TODO enqueue writer
            }
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
