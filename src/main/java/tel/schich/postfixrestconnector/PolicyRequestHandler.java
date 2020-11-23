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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static tel.schich.postfixrestconnector.PostfixRequestHandler.formUrlEncode;

public class PolicyRequestHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyRequestHandler.class);

    public static final String MODE_NAME = "policy";

    private static final char LINE_END = '\n';

    private final Endpoint endpoint;

    private final HttpClient http;
    private final String userAgent;

    public PolicyRequestHandler(Endpoint endpoint, HttpClient http, String userAgent) {
        this.endpoint = endpoint;
        this.http = http;
        this.userAgent = userAgent;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public ConnectionState createState() {
        return new PolicyConnectionState();
    }

    protected void handleRequest(SocketChannel ch, UUID id, List<Map.Entry<String, String>> params) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("{} - Policy request on endpoint {}: {}", id, endpoint.getName(), formUrlEncode(params));
        }

        final URI uri = endpoint.getTarget();

        LOGGER.info("{} - request to: {}", id, uri);

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .method("POST", HttpRequest.BodyPublishers.ofString(formUrlEncode(params)))
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Auth-Token", endpoint.getAuthToken())
                .header("X-Request-Id", id.toString())
                .timeout(Duration.ofMillis(endpoint.getRequestTimeout()))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).handle((response, err) -> {
            try {
                if (err != null) {
                    LOGGER.error("{} - error occurred during request!", id, err);
                    if (err instanceof TimeoutException) {
                        writeTemporaryError(ch, id, "REST request timed out: " + err.getMessage());
                    } else {
                        writeTemporaryError(ch, id, err.getMessage());
                    }
                    ch.close();
                    return null;
                }

                int statusCode = response.statusCode();
                LOGGER.info("{} - received response: {}", id, statusCode);
                if (statusCode == 200) {
                    // REST call successful -> return data
                    String data = response.body();
                    if (data != null) {
                        String trimmed = data.trim();
                        LOGGER.info("{} - Response: {}", id, trimmed);
                        return writeActionResponse(ch, id, trimmed);
                    } else {
                        LOGGER.warn("{} - No result!", id);
                        return writeTemporaryError(ch, id, "REST result was broken!");
                    }
                } else if (statusCode >= 400 && statusCode < 500) {
                    // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                    writePermanentError(ch, id, "REST server signaled a user error, is the connector misconfigured?");
                } else if (statusCode >= 500 && statusCode < 600) {
                    // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                    writeTemporaryError(ch, id, "REST server had an internal error!");
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write response!", e);
                try {
                    writeTemporaryError(ch, id, "REST connector encountered a problem!");
                } catch (IOException ex) {
                    LOGGER.error("{} - While recovering from an error failed to write response!", id, e);
                }
            }
            return null;
        });
    }

    private static int writePermanentError(SocketChannel ch, UUID id, String message) throws IOException {
        LOGGER.error("{} - permanent error: {}", id, message);
        return writeActionResponse(ch, id, "554 " + id + " - " + message);
    }

    public static int writeTemporaryError(SocketChannel ch, UUID id, String message) throws IOException {
        LOGGER.warn("{} - temporary error: {}", id, message);
        return writeActionResponse(ch, id, "451 " + id + " - " + message);
    }

    public static int writeActionResponse(SocketChannel ch, UUID id, String action) throws IOException {
        String text = "action=" + action + LINE_END + LINE_END;
        byte[] payload = text.getBytes(StandardCharsets.US_ASCII);

        LOGGER.info("{} - Response: {}", id, text);
        return IOUtil.writeAll(ch, payload);
    }

    private class PolicyConnectionState extends BaseConnectionState {

        private static final int READ_NAME = 1;

        private static final int READ_VALUE = 2;

        private int state = READ_NAME;

        private String pendingPairName;

        private StringBuilder pendingRead = new StringBuilder();

        private List<Map.Entry<String, String>> pendingRequest = new ArrayList<>();

        @Override
        public long read(SocketChannel ch, ByteBuffer buffer) throws IOException {
            long bytesRead = 0;
            while (buffer.remaining() > 0) {
                final byte c = buffer.get();
                bytesRead++;

                switch (state) {
                case READ_NAME:
                    if (c == LINE_END) {
                        handleRequest(ch, getId(), pendingRequest);
                        pendingRequest = new ArrayList<>();
                    } else if (c == '=') {
                        pendingPairName = pendingRead.toString();
                        pendingRead.setLength(0);
                        state = READ_VALUE;
                    } else {
                        pendingRead.append((char) c);
                    }
                    break;
                case READ_VALUE:
                    if (c == LINE_END) {
                        pendingRequest.add(Map.entry(pendingPairName, pendingRead.toString()));
                        pendingRead.setLength(0);
                        state = READ_NAME;
                    } else {
                        pendingRead.append((char) c);
                    }
                    break;
                default:
                    writePermanentError(ch, getId(), "Reached state " + state + ", but I don't know what to do...");
                    ch.close();
                }
            }
            return bytesRead;
        }

        @Override
        public void close() {
            this.pendingRead = null;
            this.pendingRequest = null;
        }
    }
}
