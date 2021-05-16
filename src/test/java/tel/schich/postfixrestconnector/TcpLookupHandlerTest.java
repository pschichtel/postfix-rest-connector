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
import tel.schich.postfixrestconnector.mocks.MockSocketChannel;
import tel.schich.postfixrestconnector.mocks.MockSocketmapLookupHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tel.schich.postfixrestconnector.LookupResponseHelper.DEFAULT_RESPONSE_VALUE_SEPARATOR;
import static tel.schich.postfixrestconnector.SocketmapLookupHandler.MODE_NAME;
import static tel.schich.postfixrestconnector.TestHelper.stringBuffer;

class TcpLookupHandlerTest {
    @Test
    public void testDecode() throws IOException {
        assertEquals("+ %&§ß", TcpLookupHandler.decodeURLEncodedData("+%20%25&%C2%A7%C3%9F"));
    }
    @Test
    public void testEncode() throws IOException {
        assertEquals("+%20%25%26%C2%A7%C3%9F", TcpLookupHandler.encodeResponseData("+ %&§ß"));
    }

}
