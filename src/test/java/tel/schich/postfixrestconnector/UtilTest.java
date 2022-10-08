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
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilTest {

    record Param(String name, String value) {
    }

    static Param param(String name, String value) {
        return new Param(name, value);
    }

    static URI appendQueryParams(URI source, Collection<Param> params) {
        String existingQuery = source.getQuery();
        String extraQuery = params.stream()
                .map(e -> URLEncoder.encode(e.name(), UTF_8) + "=" + URLEncoder.encode(e.value(), UTF_8))
                .collect(Collectors.joining("&"));
        String query = (existingQuery == null || existingQuery.isEmpty()) ? extraQuery : existingQuery + "&" + extraQuery;
        try {
            return new URI(source.getScheme(), source.getUserInfo(), source.getHost(), source.getPort(), source.getPath(), query, source.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URI syntax got invalid during query appending!", e);
        }
    }

    @Test
    public void appendQueryString() {
        var params = List.of(
            param("simply", "param"),
            param("c+m plicæted!", " param ")
        );

        URI a = URI.create("https://localhost");
        assertEquals("https://localhost?simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+", appendQueryParams(a, params).toString());

        URI b = URI.create("https://localhost?existing=arg");
        assertEquals("https://localhost?existing=arg&simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+", appendQueryParams(b, params).toString());

        URI c = URI.create("https://localhost?simply=param");
        assertEquals("https://localhost?simply=param&simply=param&c%252Bm+plic%25C3%25A6ted%2521=+param+", appendQueryParams(c, params).toString());
    }

}
