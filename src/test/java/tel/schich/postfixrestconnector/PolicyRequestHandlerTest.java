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