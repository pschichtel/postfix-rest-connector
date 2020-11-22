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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static tel.schich.postfixrestconnector.PostfixProtocol.*;

public class TcpLookupHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpLookupHandler.class);

    public static final String MODE_NAME = "tcp-lookup";

    private static final String LOOKUP_PREFIX = "get ";

    private static final int MAXIMUM_RESPONSE_LENGTH = 4096;

    private static final char END = '\n';

    private final Endpoint endpoint;

    private final AsyncHttpClient http;

    private final ObjectMapper mapper;

    public TcpLookupHandler(Endpoint endpoint, AsyncHttpClient http, ObjectMapper mapper) {
        this.endpoint = endpoint;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public ConnectionReader createReader() {
        return new TcpLookupReader();
    }

    private void handleRequest(SocketOps ch, UUID id, String rawRequest) throws IOException {
        LOGGER.info("{} - tcp-lookup request on endpoint {}: {}", id, endpoint.getName(), rawRequest);

        if (rawRequest.length() <= LOOKUP_PREFIX.length() || !rawRequest.startsWith(LOOKUP_PREFIX)) {
            writeTerminalError(ch, id, id + " - Broken request!");
            return;
        }

        String lookupKey = PostfixProtocol.decodeURLEncodedData(rawRequest.substring(LOOKUP_PREFIX.length()).trim());

        BoundRequestBuilder prepareRequest = http.prepareGet(endpoint.getTarget())
                .setHeader("X-Auth-Token", endpoint.getAuthToken())
                .setHeader("X-Request-Id", id.toString())
                .addQueryParam("key", lookupKey)
                .setRequestTimeout(endpoint.getRequestTimeout());

        prepareRequest.execute().toCompletableFuture().handleAsync((response, err) -> {
            try {
                if (err != null) {
                    LOGGER.error("{} - error occurred during request!", id, err);
                    if (err instanceof TimeoutException) {
                        writeError(ch, id, "REST request timed out: " + err.getMessage());
                    } else {
                        writeError(ch, id, err.getMessage());
                    }
                    return null;
                }

                int statusCode = response.getStatusCode();
                LOGGER.info("{} - received response: {}", id, statusCode);
                if (statusCode == 200) {
                    // REST call successful -> return data
                    String data = response.getResponseBody();
                    if (data == null) {
                        LOGGER.warn("{} - No result!", id);
                        writeError(ch, id, "REST result was broken!");
                        return null;
                    } else if (data.isEmpty()) {
                        writeNotFoundResponse(ch, id);
                        return null;
                    } else {
                        final List<String> responseValues = LookupResponseHelper.parseResponse(mapper, data);
                        if (responseValues.isEmpty()) {
                            writeNotFoundResponse(ch, id);
                            return null;
                        } else {
                            LOGGER.info("{} - Response: {}", id, responseValues);
                            writeSuccessfulResponse(ch, id, responseValues, endpoint.getListSeparator());
                            return null;
                        }
                    }
                } else if (statusCode == 404) {
                    writeNotFoundResponse(ch, id);
                    return null;
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
            return null;
        });
    }

    public static void writeSuccessfulResponse(SocketOps ch, UUID id, List<String> data, String separator)
            throws IOException {
        writeResponse(ch, id, 200, LookupResponseHelper.encodeResponse(data, separator), false);
    }

    public static void writeNotFoundResponse(SocketOps ch, UUID id) throws IOException {
        writeResponse(ch, id, 500, "key not found", false);
    }

    public static void writeError(SocketOps ch, UUID id, String message) throws IOException {
        writeResponse(ch, id, 400, message, false);
    }

    public static void writeTerminalError(SocketOps ch, UUID id, String message) throws IOException {
        writeResponse(ch, id, 400, message, true);
    }

    public static void writeResponse(SocketOps ch, UUID id, int code, String data, boolean close) throws IOException {
        String text = String.valueOf(code) + ' ' + encodeResponseData(data) + END;
        byte[] payload = text.getBytes(US_ASCII);
        if (payload.length > MAXIMUM_RESPONSE_LENGTH) {
            throw new IOException(id + " - response to long");
        }
        LOGGER.info("{} - Response: {}", id, text);
        HandlerHelper.writeAndClose(ch, payload, id, LOGGER, close);
    }

    private class TcpLookupReader implements ConnectionReader {
        private StringBuilder pendingRead = new StringBuilder();

        @Override
        public long read(ConnectionState s, SocketOps ch, ByteBuffer buffer) throws IOException {
            long bytesRead = 0;
            while (buffer.remaining() > 0) {
                final byte c = buffer.get();
                bytesRead++;
                if (c == END) {
                    handleRequest(ch, s.getId(), pendingRead.toString());
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
