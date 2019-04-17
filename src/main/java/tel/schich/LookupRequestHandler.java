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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static tel.schich.PostfixProtocol.readAsciiString;
import static tel.schich.PostfixProtocol.readToEnd;

public class LookupRequestHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LookupRequestHandler.class);
    public static final String MODE_NAME = "lookup";
    private static final String END = "\n";

    public static final String LOOKUP_PREFIX = "get ";

    private final Endpoint endpoint;
    private final AsyncHttpClient http;

    public LookupRequestHandler(Endpoint endpoint, AsyncHttpClient http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public ReadResult readRequest(ByteBuffer buf, StringBuilder out) throws IOException {
        String s = readAsciiString(buf);
        return readToEnd(out, s, END);
    }

    @Override
    public void handleReadError(SocketChannel ch) throws IOException {
        writeTemporaryError(ch, "received broken request");
        ch.close();
    }

    public void handleRequest(SocketChannel ch, String rawRequest) throws IOException {
        LOGGER.info("Lookup request on endpoint {}: {}", endpoint.getName(), rawRequest);

        if (!rawRequest.startsWith(LOOKUP_PREFIX)) {
            writePermanentError(ch, "Broken request!");
            ch.close();
        }

        String lookupKey = PostfixProtocol.decodeURLEncodedData(rawRequest.substring(LOOKUP_PREFIX.length()).trim());

        BoundRequestBuilder prepareRequest = http.prepareGet(endpoint.getTarget())
                .setHeader("X-Auth-Token", endpoint.getAuthToken())
                .setRequestTimeout(endpoint.getRequestTimeout())
                .addQueryParam("key", lookupKey);

        prepareRequest.execute().toCompletableFuture().handleAsync((response, err) -> {
            try {
                if (err != null) {
                    writeTemporaryError(ch, err.getMessage());
                    return null;
                }

                int statusCode = response.getStatusCode();
                if (statusCode == 200) {
                    // REST call successful -> return data
                    String data = response.getResponseBody();
                    if (data != null) {
                        LOGGER.info("Response: {}", data);
                        return writeSuccessfulResponse(ch, data);
                    } else {
                        LOGGER.warn("No result!");
                        return writeTemporaryError(ch, "REST result was broken!");
                    }
                } else if (statusCode >= 400 && statusCode < 500) {
                    // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                    writePermanentError(ch, "REST server signaled a user error, is the connector misconfigured?");
                } else if (statusCode >= 500 && statusCode < 600) {
                    // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                    writeTemporaryError(ch, "REST server had an internal error!");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write response!", e);
                try {
                    writeTemporaryError(ch, "REST connector encountered a problem!");
                } catch (IOException ex) {
                    LOGGER.error("Wile recovering from an error failed to write response!", e);
                }
            }
            return null;
        });
    }

    public static int writeSuccessfulResponse(SocketChannel ch, String data) throws IOException {
        return writeResponse(ch, 200, data);
    }

    public static int writePermanentError(SocketChannel ch, String message) throws IOException {
        return writeResponse(ch, 500, message);
    }

    public static int writeTemporaryError(SocketChannel ch, String message) throws IOException {
        return writeResponse(ch, 400, message);
    }

    public static int writeResponse(SocketChannel ch, int code, String data) throws IOException {
        byte[] payload = (String.valueOf(code) + ' ' + encodeResponseData(data) + "\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        return ch.write(ByteBuffer.wrap(payload));
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
