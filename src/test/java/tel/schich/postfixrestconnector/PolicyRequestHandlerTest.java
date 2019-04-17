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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.asynchttpclient.Dsl;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static tel.schich.postfixrestconnector.PostfixRequestHandler.ReadResult.*;

class PolicyRequestHandlerTest {
    private static final Endpoint endpoint =
            new Endpoint("test-policy", "http://localhost", "0.0.0.0", 9000, "test123", 1, "policy");
    private static final PolicyRequestHandler handler =
            new PolicyRequestHandler(endpoint, Dsl.asyncHttpClient(), new ObjectMapper());
    @Test
    void readCompleteRequestComplete() {
        String firstLine = "a=b\n\n";

        StringBuilder sb = new StringBuilder();
        ByteBuffer buf = stringBuffer(firstLine);

        assertEquals(COMPLETE, handler.readRequest(buf, sb));
        assertEquals(firstLine, sb.toString());
    }
    @Test
    void readCompleteRequestBroken() {
        String firstLine = "a=b\n\na";

        StringBuilder sb = new StringBuilder();
        ByteBuffer buf = stringBuffer(firstLine);

        assertEquals(BROKEN, handler.readRequest(buf, sb));
        assertEquals(0, sb.length());
    }

    @Test
    void readFragmentedRequestComplete() {
        String firstLine = "a=b\n";
        String secondLine = "\n";

        StringBuilder sb = new StringBuilder();
        ByteBuffer buf = stringBuffer(firstLine);

        assertEquals(PENDING, handler.readRequest(buf, sb));
        assertEquals(firstLine, sb.toString());

        buf = stringBuffer(secondLine);
        assertEquals(COMPLETE, handler.readRequest(buf, sb));
        assertEquals(firstLine + secondLine, sb.toString());
    }

    @Test
    void readFragmentedRequestBroken() {
        String firstLine = "a=b\n";
        String secondLine = "\na";

        StringBuilder sb = new StringBuilder();
        ByteBuffer buf = stringBuffer(firstLine);

        assertEquals(PENDING, handler.readRequest(buf, sb));
        assertEquals(firstLine, sb.toString());

        buf = stringBuffer(secondLine);
        assertEquals(BROKEN, handler.readRequest(buf, sb));
        assertEquals(firstLine, sb.toString());
    }

    static ByteBuffer stringBuffer(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII));
    }
}