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
import java.nio.channels.SocketChannel;
import java.util.List;

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
    public ConnectionState createState() {
        return new TcpConnectionState();
    }

    private void handleRequest(SocketChannel ch, String rawRequest) throws IOException {
        LOGGER.info("Lookup request on endpoint {}: {}", endpoint.getName(), rawRequest);

        if (rawRequest.length() <= LOOKUP_PREFIX.length() || !rawRequest.startsWith(LOOKUP_PREFIX)) {
            writeError(ch, "Broken request!");
            ch.close();
            return;
        }

        String lookupKey = PostfixProtocol.decodeURLEncodedData(rawRequest.substring(LOOKUP_PREFIX.length()).trim());

        BoundRequestBuilder prepareRequest = http.prepareGet(endpoint.getTarget())
                .setHeader("X-Auth-Token", endpoint.getAuthToken())
                .setRequestTimeout(endpoint.getRequestTimeout())
                .addQueryParam("key", lookupKey);

        prepareRequest.execute().toCompletableFuture().handleAsync((response, err) -> {
            try {
                if (err != null) {
                    writeError(ch, err.getMessage());
                    return null;
                }

                int statusCode = response.getStatusCode();
                if (statusCode == 200) {
                    // REST call successful -> return data
                    String data = response.getResponseBody();
                    if (data == null) {
                        LOGGER.warn("No result!");
                        return writeError(ch, "REST result was broken!");
                    } else if (data.isEmpty()) {
                        return writeNotFoundResponse(ch);
                    } else {
                        final List<String> responseValues = LookupResponseHelper.parseResponse(mapper, data);
                        if (responseValues.isEmpty()) {
                            return writeNotFoundResponse(ch);
                        } else {
                            LOGGER.info("Response: {}", responseValues);
                            return writeSuccessfulResponse(ch, responseValues, endpoint.getListSeparator());
                        }
                    }
                } else if (statusCode == 404) {
                    return writeNotFoundResponse(ch);
                } else if (statusCode >= 400 && statusCode < 500) {
                    // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                    writeError(ch, "REST server signaled a user error, is the connector misconfigured? Code: " + statusCode);
                } else if (statusCode >= 500 && statusCode < 600) {
                    // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                    writeError(ch, "REST server had an internal error: " + statusCode);
                } else {
                    writeError(ch, "REST server responded with an unspecified code: " + statusCode);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write response!", e);
                try {
                    writeError(ch, "REST connector encountered a problem!");
                } catch (IOException ex) {
                    LOGGER.error("While recovering from an error failed to write response!", e);
                }
            }
            return null;
        });
    }

    public static int writeSuccessfulResponse(SocketChannel ch, List<String> data, String separator) throws IOException {
        return writeResponse(ch, 200, LookupResponseHelper.encodeResponse(data, separator));
    }

    public static int writeNotFoundResponse(SocketChannel ch) throws IOException {
        return writeResponse(ch, 500, "key not found");
    }

    public static int writeError(SocketChannel ch, String message) throws IOException {
        return writeResponse(ch, 400, message);
    }

    public static int writeResponse(SocketChannel ch, int code, String data) throws IOException {
        byte[] payload = (String.valueOf(code) + ' ' + encodeResponseData(data) + END).getBytes(US_ASCII);
        if (payload.length > MAXIMUM_RESPONSE_LENGTH)
        {
            throw new IOException("response to long");
        }
        return ch.write(ByteBuffer.wrap(payload));
    }

    private class TcpConnectionState implements ConnectionState {
        private StringBuilder pendingRead = new StringBuilder();

        @Override
        public long read(SocketChannel ch, ByteBuffer buffer) throws IOException {
            long bytesRead = 0;
            while (buffer.remaining() > 0) {
                final byte c = buffer.get();
                bytesRead++;
                if (c == END) {
                    handleRequest(ch, pendingRead.toString());
                    pendingRead.setLength(0);
                } else {
                    pendingRead.append((char)c);
                }
            }
            return bytesRead;
        }
    }
}
