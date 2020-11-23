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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.US_ASCII;

public interface PostfixRequestHandler {
    Endpoint getEndpoint();

    ConnectionState createState();

    static String formUrlEncode(Collection<Map.Entry<String, String>> params) {
        return params.stream().map(p -> encode(p.getKey(), US_ASCII) + "=" + encode(p.getValue(), US_ASCII))
                .collect(Collectors.joining("&"));
    }
}
