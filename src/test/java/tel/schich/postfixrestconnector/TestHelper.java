package tel.schich.postfixrestconnector;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TestHelper {
    static ByteBuffer stringBuffer(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII));
    }
}
