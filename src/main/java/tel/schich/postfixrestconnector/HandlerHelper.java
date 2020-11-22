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

import java.util.UUID;

public abstract class HandlerHelper {
    private HandlerHelper() {}

    public static void writeAndClose(SocketOps ch, byte[] payload, UUID id, Logger logger, boolean close) {
        if (close) {
            ch.writeAndClose(payload, t -> {
                if (t != null) {
                    logger.error("{} - write and/or close failed!", id, t);
                }
            });
        } else {
            ch.write(payload, writeError -> {
                if (writeError != null) {
                    logger.error("{} - write failed, closing...", id, writeError);
                    ch.close(closeError -> {
                        if (closeError != null) {
                            logger.error("{} - close failed!", id, closeError);
                        }
                    });
                }
            });
        }
    }
}
