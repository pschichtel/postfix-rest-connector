package tel.schich.postfixrestconnector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class NetstringTest {

    @Test
    void parseOne() throws IOException {
        Throwable t = assertThrows(IOException.class, () -> {
            assertEquals("a", Netstring.parseOne("1:a,2:bc,"));
        });
        assertNull(t.getCause());

        assertEquals("a", Netstring.parseOne("1:a,"));
    }

    @Test
    void parse() throws IOException {
        assertEquals(Arrays.asList("a", "bc"), Netstring.parse("1:a,2:bc,"));
    }

    @Test
    void compile() {
        assertEquals("0:,", Netstring.compile(Collections.singletonList("")));
        assertEquals("", Netstring.compile(Collections.emptyList()));
        assertEquals("1:a,2:bc,", Netstring.compile(Arrays.asList("a", "bc")));
    }

    @Test
    void compileOne() {
        assertEquals("0:,", Netstring.compileOne(""));
    }
}
