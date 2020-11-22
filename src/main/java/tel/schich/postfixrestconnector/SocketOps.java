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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import static java.nio.channels.SelectionKey.OP_WRITE;

public interface SocketOps {
    void submitOp(Op op, Continuation k);

    default void write(byte[] data, Continuation k) {
        submitOp(new WriteOp(data, 0, data.length), k);
    }

    default void close(Continuation k) {
        submitOp(CloseOp.INSTANCE, k);
    }

    default void writeAndClose(byte[] data, Continuation k) {
        write(data, writeError -> {
            if (writeError != null) {
                close(closeError -> {
                    if (closeError != null) {
                        closeError.addSuppressed(writeError);
                        k.run(closeError);
                    } else {
                        k.run(writeError);
                    }
                });
            } else {
                close(k);
            }
        });
    }

    interface Op {
        ConnectionWriter apply(SelectionKey key, SocketChannel ch, UUID id, Continuation k);
    }

    class WriteOp implements Op {
        private static final Logger LOGGER = LoggerFactory.getLogger(WriteOp.class);

        private final byte[] data;
        private final int offset;
        private final int length;

        public WriteOp(byte[] data, int offset, int length) {
            this.data = data;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public ConnectionWriter apply(SelectionKey key, SocketChannel ch, UUID id, Continuation k) {
            key.interestOps(key.interestOps() | OP_WRITE);
            LOGGER.info("{} - write key: {}", id, key);
            return new ConnectionWriter(id, key, data, offset, length, t -> {
                key.interestOps(key.interestOps() & ~OP_WRITE);
                k.run(t);
            });
        }
    }

    class CloseOp implements Op {
        private static final Logger LOGGER = LoggerFactory.getLogger(CloseOp.class);

        public static final CloseOp INSTANCE = new CloseOp();

        @Override
        public ConnectionWriter apply(SelectionKey key, SocketChannel ch, UUID id, Continuation k) {
            try {
                ch.close();
                LOGGER.debug("{} - connection closed!", id);
                k.run(null);
            } catch (IOException e) {
                LOGGER.error("{} - closing connection failed!", id, e);
                k.run(e);
            }
            return null;
        }
    }

}
