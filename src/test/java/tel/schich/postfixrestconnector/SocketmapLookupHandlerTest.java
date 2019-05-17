package tel.schich.postfixrestconnector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.junit.jupiter.api.Assertions.*;
import static tel.schich.postfixrestconnector.PostfixRequestHandler.ReadResult.COMPLETE;
import static tel.schich.postfixrestconnector.SocketmapLookupHandler.MODE_NAME;
import static tel.schich.postfixrestconnector.TestHelper.stringBuffer;

class SocketmapLookupHandlerTest {
    private static final Endpoint endpoint =
            new Endpoint("test-policy", "http://localhost", "0.0.0.0", 9000, "test123", 1, MODE_NAME);

    @Test
    public void testRequest() throws IOException {
        final SocketmapLookupHandler h = new SocketmapLookupHandler(endpoint, asyncHttpClient(), new ObjectMapper());
        final String s = "10:0123456789,";
        final ByteBuffer b = stringBuffer(s);
        final StringBuilder sb = new StringBuilder();
        final SocketChannel sc = new MockSocketChannel();
        assertEquals(COMPLETE, h.readRequest(b, sb));
        assertEquals(s, sb.toString());
        h.handleRequest(sc, sb.toString());
    }

}
