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
