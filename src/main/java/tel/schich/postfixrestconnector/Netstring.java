package tel.schich.postfixrestconnector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Netstring {

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
                    throw new IOException("Expected ',' at " + offset + ", but got '" + s.charAt(commaPos) + "' after data!");
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
