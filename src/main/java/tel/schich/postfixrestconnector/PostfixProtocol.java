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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;

import tel.schich.postfixrestconnector.PostfixRequestHandler.ReadResult;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class PostfixProtocol {

    public static String readAsciiString(ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            return "";
        }
        if (buf.isDirect()) {
            byte[] jbuf = new byte[buf.remaining()];
            buf.get(jbuf);
            return new String(jbuf, US_ASCII);
        } else {
            return new String(buf.array(), buf.position(), buf.remaining(), US_ASCII);
        }
    }

    public static String decodeURLEncodedData(String data) {
        try {
            return URLDecoder.decode(data, US_ASCII.name());
        } catch (UnsupportedEncodingException e) {
            return data;
        }
    }

    public static ReadResult readToEnd(StringBuilder out, String input, String end) {
        int endIndex = input.indexOf(end);
        if (endIndex == -1) {
            out.append(input);
            return ReadResult.PENDING;
        }

        if (endIndex != (input.length() - end.length())) {
            return ReadResult.BROKEN;
        }

        out.append(input);
        return ReadResult.COMPLETE;
    }
}
