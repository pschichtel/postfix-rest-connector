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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.asynchttpclient.Param;
import org.junit.jupiter.api.Test;

import tel.schich.postfixrestconnector.mocks.MockPolicyRequestHandler;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static tel.schich.postfixrestconnector.LookupResponseHelper.DEFAULT_RESPONSE_VALUE_SEPARATOR;
import static tel.schich.postfixrestconnector.TestHelper.stringBuffer;
import static tel.schich.postfixrestconnector.mocks.MockSocketChannel.DEFAULT;

class PolicyRequestHandlerTest {
    private static final Endpoint ENDPOINT =
            new Endpoint("test-policy", "http://localhost", "0.0.0.0", 9000, "test123", 1, "policy", DEFAULT_RESPONSE_VALUE_SEPARATOR);
    private static final MockPolicyRequestHandler HANDLER = new MockPolicyRequestHandler(ENDPOINT);

    @Test
    void readCompleteRequestComplete() throws IOException {
        String firstLine = "a=b\n\n";

        ByteBuffer buf = stringBuffer(firstLine);
        ConnectionState state = HANDLER.createState();
        assertEquals(buf.remaining(), state.read(DEFAULT, buf));
        List<Param> data = HANDLER.getData();

        assertEquals(1, data.size());
        assertEquals(data.get(0), new Param("a", "b"));
    }

    @Test
    void readCompleteRequestBroken() throws IOException {
        String firstLine = "a=b\n\na";

        ByteBuffer buf = stringBuffer(firstLine);
        ConnectionState state = HANDLER.createState();
        assertEquals(buf.remaining(), state.read(DEFAULT, buf));
        List<Param> data = HANDLER.getData();

        assertEquals(1, data.size());
        assertEquals(data.get(0), new Param("a", "b"));
    }

    @Test
    void readFragmentedRequestComplete() throws IOException {
        String firstLine = "a=b\n";
        String secondLine = "\n";

        ConnectionState state = HANDLER.createState();
        ByteBuffer buf = stringBuffer(firstLine);

        assertEquals(buf.remaining(), state.read(DEFAULT, buf));
        List<Param> data = HANDLER.getData();
        assertNull(data);

        buf = stringBuffer(secondLine);
        assertEquals(buf.remaining(), state.read(DEFAULT, buf));
        data = HANDLER.getData();
        assertEquals(singletonList(new Param("a", "b")), data);
    }

    @Test
    void readFragmentedRequestBroken() throws IOException {
        String firstLine = "a=b\n";
        String secondLine = "\na";
        String rest = "=c\n\n";

        ConnectionState state = HANDLER.createState();
        ByteBuffer buf = stringBuffer(firstLine);

        assertEquals(buf.remaining(), state.read(DEFAULT, buf));
        assertNull(HANDLER.getData());

        buf = stringBuffer(secondLine);

        assertEquals(buf.remaining(), state.read(DEFAULT, buf));
        assertEquals(singletonList(new Param("a", "b")), HANDLER.getData());

        buf = stringBuffer(rest);

        assertEquals(buf.remaining(), state.read(DEFAULT, buf));
        assertEquals(singletonList(new Param("a", "c")), HANDLER.getData());
    }

}
