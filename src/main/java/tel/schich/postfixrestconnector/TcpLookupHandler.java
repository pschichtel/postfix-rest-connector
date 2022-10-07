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
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

public class TcpLookupHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpLookupHandler.class);

    public static final String MODE_NAME = "tcp-lookup";

    private static final String LOOKUP_PREFIX = "get ";

    private static final int MAXIMUM_RESPONSE_LENGTH = 4096;

    private static final char END = '\n';

    private static final String PLUS = "+";
    private static final String PLUS_URL_ENCODED = "%2B";
    private static final String SPACE_URL_ENCODED = "%20";

    private final Endpoint endpoint;

    private final HttpClient http;

    private final ObjectMapper mapper;
    private final String userAgent;

    public TcpLookupHandler(Endpoint endpoint, HttpClient http, ObjectMapper mapper, String userAgent) {
        this.endpoint = endpoint;
        this.http = http;
        this.mapper = mapper;
        this.userAgent = userAgent;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public ConnectionState createState() {
        return new TcpConnectionState();
    }

    private static void printRequest(UUID id, HttpRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(req.method());
        sb.append(' ');
        sb.append(req.uri().toASCIIString());

        for (Map.Entry<String, List<String>> values : req.headers().map().entrySet()) {
            for (String value : values.getValue()) {
                sb.append('\n');
                sb.append(values.getKey());
                sb.append(": ");
                sb.append(value);
            }
        }
        sb.append("\n\n");
        req.bodyPublisher().ifPresent(publisher -> sb.append("<body>"));

        LOGGER.info("Request of {}:\n{}", id, sb);
    }

    private void handleRequest(SocketChannel ch, UUID id, String rawRequest) throws IOException {
        LOGGER.info("{} - tcp-lookup request on endpoint {}: {}", id, endpoint.name(), rawRequest);

        if (rawRequest.length() <= LOOKUP_PREFIX.length() || !rawRequest.startsWith(LOOKUP_PREFIX)) {
            writeError(ch, id, "Broken request!");
            ch.close();
            return;
        }

        String lookupKey = decodeURLEncodedData(rawRequest.substring(LOOKUP_PREFIX.length()).trim());
        final URI uri = Util.appendQueryParams(endpoint.target(), Map.of("key", lookupKey));

        LOGGER.info("{} - request to: {}", id, uri);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", userAgent)
                .header("X-Auth-Token", endpoint.authToken())
                .header("X-Request-Id", id.toString())
                .timeout(Duration.ofMillis(endpoint.requestTimeout()))
                .build();

        printRequest(id, request);

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) -> {
            try {
                if (err != null) {
                    LOGGER.error("{} - error occurred during request!", id, err);
                    if (err instanceof TimeoutException) {
                        writeError(ch, id, "REST request timed out: " + err.getMessage());
                    } else {
                        writeError(ch, id, err.getMessage());
                    }
                    return;
                }

                int statusCode = response.statusCode();
                LOGGER.info("{} - received response: {}", id, statusCode);
                if (statusCode == 200) {
                    // REST call successful -> return data
                    String data = response.body();
                    if (data == null) {
                        LOGGER.warn("{} - No result!", id);
                        writeError(ch, id, "REST result was broken!");
                    } else if (data.isEmpty()) {
                        writeNotFoundResponse(ch, id);
                    } else {
                        final List<String> responseValues = LookupResponseHelper.parseResponse(mapper, data);
                        if (responseValues.isEmpty()) {
                            writeNotFoundResponse(ch, id);
                        } else {
                            LOGGER.info("{} - Response: {}", id, responseValues);
                            writeSuccessfulResponse(ch, id, responseValues, endpoint.listSeparator());
                        }
                    }
                } else if (statusCode == 404) {
                    writeNotFoundResponse(ch, id);
                } else if (statusCode >= 400 && statusCode < 500) {
                    // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                    writeError(ch, id,
                            "REST server signaled a user error, is the connector misconfigured? Code: " + statusCode);
                } else if (statusCode >= 500 && statusCode < 600) {
                    // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                    writeError(ch, id, "REST server had an internal error: " + statusCode);
                } else {
                    writeError(ch, id, "REST server responded with an unspecified code: " + statusCode);
                }
            } catch (IOException e) {
                LOGGER.error("{} - Failed to write response!", id, e);
                try {
                    writeError(ch, id, "REST connector encountered a problem!");
                } catch (IOException ex) {
                    LOGGER.error("{} - While recovering from an error failed to write response!", id, e);
                }
            }
        });
    }

    public static int writeSuccessfulResponse(SocketChannel ch, UUID id, List<String> data, String separator)
            throws IOException {
        return writeResponse(ch, id, 200, LookupResponseHelper.encodeResponse(data, separator));
    }

    public static int writeNotFoundResponse(SocketChannel ch, UUID id) throws IOException {
        return writeResponse(ch, id, 500, id + " - key not found");
    }

    public static int writeError(SocketChannel ch, UUID id, String message) throws IOException {
        return writeResponse(ch, id, 400, id + " - " + message);
    }

    public static int writeResponse(SocketChannel ch, UUID id, int code, String data) throws IOException {
        String text = String.valueOf(code) + ' ' + encodeResponseData(data) + END;
        byte[] payload = text.getBytes(US_ASCII);
        if (payload.length > MAXIMUM_RESPONSE_LENGTH) {
            throw new IOException(id + " - response to long");
        }
        LOGGER.info("{} - Response: {}", id, text);
        return Util.writeAll(ch, payload);
    }

    static String decodeURLEncodedData(String data) {
        return URLDecoder.decode(data.replace(PLUS, PLUS_URL_ENCODED), UTF_8);
    }

    static String encodeResponseData(String data) {
        return URLEncoder.encode(data, UTF_8)
                .replace(PLUS, SPACE_URL_ENCODED)
                .replace(PLUS_URL_ENCODED, PLUS);
    }

    private class TcpConnectionState extends BaseConnectionState {
        private StringBuilder pendingRead = new StringBuilder();

        @Override
        public long read(SocketChannel ch, ByteBuffer buffer) throws IOException {
            long bytesRead = 0;
            while (buffer.remaining() > 0) {
                final byte c = buffer.get();
                bytesRead++;
                if (c == END) {
                    handleRequest(ch, getId(), pendingRead.toString());
                    pendingRead.setLength(0);
                } else {
                    pendingRead.append((char) c);
                }
            }
            return bytesRead;
        }

        @Override
        public void close() {
            this.pendingRead = null;
        }
    }
}
