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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class LookupResponseHelper {
    private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<List<String>>() {};
    public static final String DEFAULT_RESPONSE_VALUE_SEPARATOR = ",";

    private LookupResponseHelper() {}

    public static List<String> parseResponse(ObjectMapper mapper, String response) throws IOException {
        return mapper.readValue(response, STRING_LIST_REF);
    }

    public static String encodeResponse(Iterable<String> values, String separator) {
        final Iterator<String> it = values.iterator();
        if (!it.hasNext()) {
            return "";
        }

        StringBuilder out = new StringBuilder(it.next());
        while (it.hasNext()) {
            out.append(separator).append(it.next());
        }
        return out.toString();
    }
}
