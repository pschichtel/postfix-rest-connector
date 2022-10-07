/*
 * Postfix REST Connector - A simple TCP server that can be used as tcp lookup, socketmap lookup or policy check server for the Postfix mail server.
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

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tel.schich.postfixrestconnector.Util.param;

public class UtilTest {

    @Test
    public void appendQueryString() {
        var params = List.of(
            param("simply", "param"),
            param("c+m plicæted!", " param ")
        );

        URI a = URI.create("https://localhost");
        assertEquals("https://localhost?simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+", Util.appendQueryParams(a, params).toString());

        URI b = URI.create("https://localhost?existing=arg");
        assertEquals("https://localhost?existing=arg&simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+", Util.appendQueryParams(b, params).toString());

        URI c = URI.create("https://localhost?simply=param");
        assertEquals("https://localhost?simply=param&simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+", Util.appendQueryParams(c, params).toString());
    }

}
