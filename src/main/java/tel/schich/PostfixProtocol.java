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
package tel.schich;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class PostfixProtocol {

    public static String decodeRequestData(String data) {
        try {
            return URLDecoder.decode(data, US_ASCII.name());
        } catch (UnsupportedEncodingException e) {
            return data;
        }
    }

    public static int writeSuccessfulResponse(SocketChannel ch, ByteBuffer buf, List<String> results) throws
            IOException {
        StringBuilder data = new StringBuilder();
        Iterator<String> it = results.iterator();
        if (it.hasNext()) {
            data.append(it.next());
            while (it.hasNext()) {
                data.append(' ').append(it.next());
            }
            return writeResponse(ch, buf, 200, data.toString());
        } else {
            return -1;
        }
    }

    public static int writePermanentError(SocketChannel ch, ByteBuffer buf, String message) throws IOException {
        return writeResponse(ch, buf, 500, message);
    }

    public static int writeTemporaryError(SocketChannel ch, ByteBuffer buf, String message) throws IOException {
        return writeResponse(ch, buf, 400, message);
    }

    public static int writeResponse(SocketChannel ch, ByteBuffer buf, int code, String data) throws IOException {
        byte[] payload = (String.valueOf(code) + ' ' + encodeResponseData(data) + "\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        buf.clear();
        buf.put(payload);
        buf.flip();
        return ch.write(buf);
    }

    public static String encodeResponseData(String data) {
        StringBuilder out = new StringBuilder();
        for (char c : data.toCharArray()) {
            if (c <= 32) {
                out.append('%');
                String hex = Integer.toHexString(c);
                if (hex.length() == 1) {
                    out.append('0');
                }
                out.append(hex);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
