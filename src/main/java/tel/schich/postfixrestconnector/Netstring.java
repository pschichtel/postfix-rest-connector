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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Netstring {

    private Netstring() {
    }

    public static final String EMPTY = "0:,";

    public static String parseOne(String s) throws IOException {
        final List<String> strings = parse(s);
        if (strings.size() > 1) {
            throw new IOException("Multiple netstrings detected, but only one requested!");
        }
        return strings.get(0);
    }

    public static List<String> parse(String s) throws IOException {
        int colonPos;
        int offset = 0;
        int runLength;
        int commaPos;
        List<String> out = new ArrayList<>();

        try {
            while (offset < s.length()) {
                colonPos = s.indexOf(':', offset);
                if (colonPos == -1) {
                    throw new IOException("Expected to find a ':' after " + offset + ", but didn't!");
                }
                runLength = Integer.parseInt(s.substring(offset, colonPos));
                commaPos = colonPos + 1 + runLength;
                if (s.charAt(commaPos) != ',') {
                    throw new IOException(
                            "Expected ',' at " + offset + ", but got '" + s.charAt(commaPos) + "' after data!");
                }
                out.add(s.substring(colonPos + 1, commaPos));
                offset = commaPos + 1;
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            throw new IOException("Failed to parse netstring!", e);
        }

        return out;
    }

    public static String compile(List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String s : strings) {
            compile(s, out);
        }

        return out.toString();
    }

    public static String compileOne(String s) {
        if (s.isEmpty()) {
            return EMPTY;
        }

        StringBuilder out = new StringBuilder();
        compile(s, out);
        return out.toString();
    }

    private static void compile(String s, StringBuilder out) {
        if (s.isEmpty()) {
            out.append(EMPTY);
        } else {
            out.append(s.length()).append(':').append(s).append(',');
        }
    }
}
