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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import tel.schich.postfixrestconnector.mocks.MockSocketChannel;
import tel.schich.postfixrestconnector.mocks.MockSocketmapLookupHandler;

import static org.junit.jupiter.api.Assertions.*;
import static tel.schich.postfixrestconnector.LookupResponseHelper.DEFAULT_RESPONSE_VALUE_SEPARATOR;
import static tel.schich.postfixrestconnector.SocketmapLookupHandler.MODE_NAME;
import static tel.schich.postfixrestconnector.TestHelper.stringBuffer;

class SocketmapLookupHandlerTest {
    private static final Endpoint ENDPOINT =
            new Endpoint("test-policy", "http://localhost", "0.0.0.0", 9000, "test123", 1, MODE_NAME, DEFAULT_RESPONSE_VALUE_SEPARATOR);
    private static final MockSocketmapLookupHandler HANDLER = new MockSocketmapLookupHandler(ENDPOINT);

    @Test
    public void testRequest() throws IOException {
        final String d = "0123456789";
        final String s = "10:" + d + ",";
        final ByteBuffer b = stringBuffer(s);
        final SocketChannel sc = new MockSocketChannel();
        ConnectionState state = HANDLER.createState();
        assertEquals(s.length(), state.read(sc, b));
        assertEquals(d, HANDLER.getData());
    }

}
