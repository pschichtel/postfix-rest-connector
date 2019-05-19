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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static tel.schich.postfixrestconnector.PostfixProtocol.*;

public class SocketmapLookupHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketmapLookupHandler.class);
    public static final String MODE_NAME = "socketmap-lookup";

    private static final String LOOKUP_PREFIX = "get ";
    private static final int MAXIMUM_RESPONSE_LENGTH = 10000;
    private static final String END = ",";

    private final Endpoint endpoint;
    private final AsyncHttpClient http;
    private final ObjectMapper mapper;

    public SocketmapLookupHandler(Endpoint endpoint, AsyncHttpClient http, ObjectMapper mapper) {
        this.endpoint = endpoint;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public ReadResult readRequest(ByteBuffer buf, StringBuilder out) {
        String s = readAsciiString(buf);
        return readToEnd(out, s, END);
    }

    @Override
    public void handleReadError(SocketChannel ch) throws IOException {
        writePermError(ch, "received broken request");
        ch.close();
    }

    public void handleRequest(SocketChannel ch, String rawRequest) throws IOException {
        LOGGER.info("Lookup request on endpoint {}: {}", endpoint.getName(), rawRequest);

        String requestData = Netstring.parseOne(rawRequest);

        final int spacePos = requestData.indexOf(' ');
        if (spacePos == -1) {
            writeBrokenRequestErrorAndClose(ch, "invalid request format");
            return;
        }

        final String name = PostfixProtocol.decodeURLEncodedData(requestData.substring(0, spacePos));
        final String lookupKey = PostfixProtocol.decodeURLEncodedData(requestData.substring(spacePos + 1));

        BoundRequestBuilder prepareRequest = http.prepareGet(endpoint.getTarget())
                .setHeader("X-Auth-Token", endpoint.getAuthToken())
                .setRequestTimeout(endpoint.getRequestTimeout())
                .addQueryParam("name", name)
                .addQueryParam("key", lookupKey);

        prepareRequest.execute().toCompletableFuture().handleAsync((response, err) -> {
            try {
                if (err != null) {
                    if (err instanceof TimeoutException) {
                        writeTimeoutError(ch, "REST request timed out: " + err.getMessage());
                    } else {
                        writeTempError(ch, err.getMessage());
                    }
                    return null;
                }

                int statusCode = response.getStatusCode();
                if (statusCode == 200) {
                    // REST call successful -> return data
                    String data = response.getResponseBody();
                    if (data == null) {
                        LOGGER.warn("No result!");
                        return writeTempError(ch, "REST result was broken!");
                    } else if (data.isEmpty()) {
                        return writeNotFoundResponse(ch);
                    } else {
                        final List<String> responseValues = LookupResponseHelper.parseResponse(mapper, data);
                        if (responseValues.isEmpty()) {
                            return writeNotFoundResponse(ch);
                        } else {
                            LOGGER.info("Response: {}", responseValues);

                            return writeOkResponse(ch, responseValues, endpoint.getListSeparator());
                        }
                    }
                } else if (statusCode == 404) {
                    return writeNotFoundResponse(ch);
                } else if (statusCode >= 400 && statusCode < 500) {
                    // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                    writePermError(ch, "REST server signaled a user error, is the connector misconfigured? Code: " + statusCode);
                } else if (statusCode >= 500 && statusCode < 600) {
                    // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                    writeTempError(ch, "REST server had an internal error: " + statusCode);
                } else {
                    writeTempError(ch, "REST server responded with an unspecified code: " + statusCode);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write response!", e);
                try {
                    writeTempError(ch, "REST connector encountered a problem!");
                } catch (IOException ex) {
                    LOGGER.error("While recovering from an error failed to write response!", e);
                }
            }
            return null;
        });
    }

    public static int writeOkResponse(SocketChannel ch, List<String> data, String separator) throws IOException {
        return writeResponse(ch, "OK " + LookupResponseHelper.encodeResponse(data, separator));
    }

    public static int writeNotFoundResponse(SocketChannel ch) throws IOException {
        return writeResponse(ch, "");
    }

    public static void writeBrokenRequestErrorAndClose(SocketChannel ch, String reason) throws IOException {
        writePermError(ch, "Broken request! (" + reason + ")");
        ch.close();
    }

    public static int writeTimeoutError(SocketChannel ch, String message) throws IOException {
        return writeResponse(ch, "TIMEOUT " + message);
    }

    public static int writeTempError(SocketChannel ch, String message) throws IOException {
        return writeResponse(ch, "TEMP " + message);
    }

    public static int writePermError(SocketChannel ch, String message) throws IOException {
        return writeResponse(ch, "PERM " + message);
    }

    public static int writeResponse(SocketChannel ch, String data) throws IOException {
        if (data.length() > MAXIMUM_RESPONSE_LENGTH)
        {
            throw new IOException("response to long");
        }
        byte[] payload = Netstring.compileOne(data).getBytes(US_ASCII);
        return ch.write(ByteBuffer.wrap(payload));
    }

}
