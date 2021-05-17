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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class SocketmapLookupHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketmapLookupHandler.class);

    public static final String MODE_NAME = "socketmap-lookup";

    private static final int MAXIMUM_RESPONSE_LENGTH = 10000;

    private static final char END = ',';

    private final Endpoint endpoint;

    private final HttpClient http;

    private final ObjectMapper mapper;
    private final String userAgent;

    public SocketmapLookupHandler(Endpoint endpoint, HttpClient http, ObjectMapper mapper, String userAgent) {
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
        return new SocketMapConnectionState();
    }

    protected void handleRequest(SocketChannel ch, UUID id, String requestData) throws IOException {
        LOGGER.info("{} - socketmap-lookup request on endpoint {}: {}", id, endpoint.getName(), requestData);

        final int spacePos = requestData.indexOf(' ');
        if (spacePos == -1) {
            writeBrokenRequestErrorAndClose(ch, id, "invalid request format");
            return;
        }

        // splitting at the first space assumes, that map names with spaces cannot be configured in postfix
        // compared to the TCP lookup, these values are not URL encoded
        final String name = requestData.substring(0, spacePos);
        final String lookupKey = requestData.substring(spacePos + 1);

        final URI uri;
        try {
            uri = new URIBuilder(endpoint.getTarget())
                    .addParameter("name", name)
                    .addParameter("key", lookupKey)
                    .build();
        } catch (URISyntaxException e) {
            throw new IOException("failed to build URI", e);
        }

        LOGGER.info("{} - request to: {}", id, uri);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", userAgent)
                .header("X-Auth-Token", endpoint.getAuthToken())
                .header("X-Request-Id", id.toString())
                .timeout(Duration.ofMillis(endpoint.getRequestTimeout()))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, err) -> {
            try {
                if (err != null) {
                    LOGGER.error("{} - error occurred during request!", id, err);
                    if (err instanceof TimeoutException) {
                        writeTimeoutError(ch, id, "REST request timed out: " + err.getMessage());
                    } else {
                        writeTempError(ch, id, err.getMessage());
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
                        writeTempError(ch, id, "REST result was broken!");
                    } else if (data.isEmpty()) {
                        writeNotFoundResponse(ch, id);
                    } else {
                        final List<String> responseValues = LookupResponseHelper.parseResponse(mapper, data);
                        if (responseValues.isEmpty()) {
                            writeNotFoundResponse(ch, id);
                        } else {
                            LOGGER.info("{} - Response: {}", id, responseValues);
                            writeOkResponse(ch, id, responseValues, endpoint.getListSeparator());
                        }
                    }
                } else if (statusCode == 404) {
                    writeNotFoundResponse(ch, id);
                } else if (statusCode >= 400 && statusCode < 500) {
                    // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                    writePermError(ch, id,
                            "REST server signaled a user error, is the connector misconfigured? Code: " + statusCode);
                } else if (statusCode >= 500 && statusCode < 600) {
                    // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                    writeTempError(ch, id, "REST server had an internal error: " + statusCode);
                } else {
                    writeTempError(ch, id, "REST server responded with an unspecified code: " + statusCode);
                }
            } catch (IOException e) {
                LOGGER.error("{} - Failed to write response!", id, e);
                try {
                    writeTempError(ch, id, "REST connector encountered a problem!");
                } catch (IOException ex) {
                    LOGGER.error("{} - While recovering from an error failed to write response!", id, e);
                }
            }
        });
    }

    public static int writeOkResponse(SocketChannel ch, UUID id, List<String> data, String separator) throws IOException {
        return writeResponse(ch, id, "OK " + LookupResponseHelper.encodeResponse(data, separator));
    }

    public static int writeNotFoundResponse(SocketChannel ch, UUID id) throws IOException {
        return writeResponse(ch, id, "NOTFOUND ");
    }

    public static void writeBrokenRequestErrorAndClose(SocketChannel ch, UUID id, String reason) throws IOException {
        LOGGER.error("{} - broken request: {}", id, reason);
        writePermError(ch, id, "Broken request! (" + reason + ")");
        ch.close();
    }

    public static int writeTimeoutError(SocketChannel ch, UUID id, String message) throws IOException {
        return writeResponse(ch, id, "TIMEOUT " + id + " - " + message);
    }

    public static int writeTempError(SocketChannel ch, UUID id, String message) throws IOException {
        return writeResponse(ch, id, "TEMP " + id + " - " + message);
    }

    public static int writePermError(SocketChannel ch, UUID id, String message) throws IOException {
        return writeResponse(ch, id, "PERM " + id + " - " + message);
    }

    public static int writeResponse(SocketChannel ch, UUID id, String data) throws IOException {
        if (data.length() > MAXIMUM_RESPONSE_LENGTH) {
            throw new IOException(id + " - response to long");
        }
        String text = Netstring.compileOne(data);
        LOGGER.info("{} - Response: {}", id, text);
        byte[] payload = text.getBytes(US_ASCII);
        return IOUtil.writeAll(ch, payload);
    }

    private class SocketMapConnectionState  extends BaseConnectionState {

        private static final int READ_LENGTH = 1;

        private static final int READ_VALUE = 2;

        private static final int READ_END = 3;

        private int state = READ_LENGTH;

        private long length = 0;

        private StringBuilder pendingRead = new StringBuilder();

        @Override
        public long read(SocketChannel ch, ByteBuffer buffer) throws IOException {
            long bytesRead = 0;
            while (buffer.remaining() > 0) {
                final byte c = buffer.get();
                bytesRead++;

                switch (state) {
                case READ_LENGTH:
                    if (c == ':') {
                        state = READ_VALUE;
                        pendingRead.setLength(0);
                    } else {
                        int digit = c - '0';
                        if (digit < 0 || digit > 9) {
                            writeBrokenRequestErrorAndClose(ch, getId(), "Expected a digit, but got: " + (char) c);
                        }
                        length = length * 10 + digit;
                    }
                    break;
                case READ_VALUE:
                    if (pendingRead.length() < length) {
                        pendingRead.append((char) c);
                    }

                    if (pendingRead.length() >= length) {
                        state = READ_END;
                    }
                    break;
                case READ_END:
                    if (c == END) {
                        state = READ_LENGTH;
                        length = 0;
                        handleRequest(ch, getId(), pendingRead.toString());
                    } else {
                        writeBrokenRequestErrorAndClose(ch, getId(), "Expected comma, but got: " + (char) c);
                    }
                    break;
                default:
                    writeBrokenRequestErrorAndClose(ch, getId(), "Reached state " + state + ", but I don't know what to do...");
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
