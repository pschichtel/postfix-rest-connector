package tel.schich.postfixrestconnector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class LookupResponseHelper {
    private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<List<String>>() {};
    private static final char RESPONSE_VALUE_SEPARATOR = ' ';

    public static List<String> parseResponse(ObjectMapper mapper, String response) throws IOException {
        return mapper.readValue(response, STRING_LIST_REF);
    }

    public static String encodeResponse(Iterable<String> values) {
        final Iterator<String> it = values.iterator();
        if (!it.hasNext()) {
            return "";
        }

        StringBuilder out = new StringBuilder(it.next());
        while (it.hasNext()) {
            out.append(RESPONSE_VALUE_SEPARATOR).append(it.next());
        }
        return out.toString();
    }
}
