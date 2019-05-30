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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolicyRequestHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyRequestHandler.class);
    public static final String MODE_NAME = "policy";

    private static final char LINE_END = '\n';

    private final Endpoint endpoint;
    private final AsyncHttpClient http;

    public PolicyRequestHandler(Endpoint endpoint, AsyncHttpClient http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public ConnectionState createState() {
        return new PolicyConnectionState();
    }

    protected void handleRequest(SocketChannel ch, List<Param> params) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Policy request on endpoint {}: {}", endpoint.getName(), paramsToString(params));
        }

        BoundRequestBuilder prepareRequest = http.preparePost(endpoint.getTarget())
                .setHeader("X-Auth-Token", endpoint.getAuthToken())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setRequestTimeout(endpoint.getRequestTimeout())
                .setFormParams(params);

        prepareRequest.execute().toCompletableFuture().handleAsync((response, err) -> {
            try {
                if (err != null) {
                    writeTemporaryError(ch, err.getMessage());
                    ch.close();
                    return null;
                }

                int statusCode = response.getStatusCode();
                if (statusCode == 200) {
                    // REST call successful -> return data
                    String data = response.getResponseBody();
                    if (data != null) {
                        String trimmed = data.trim();
                        LOGGER.info("Response: {}", trimmed);
                        return writeActionResponse(ch, trimmed);
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

    private static int writePermanentError(SocketChannel ch, String message) throws IOException {
        return writeActionResponse(ch, "554 " + message);
    }

    public static int writeTemporaryError(SocketChannel ch, String message) throws IOException {
        return writeActionResponse(ch, "451 " + message);
    }

    public static int writeActionResponse(SocketChannel ch, String action) throws IOException {
        String text = "action=" + action + LINE_END + LINE_END;
        byte[] payload = text.getBytes(StandardCharsets.US_ASCII);

        LOGGER.info("Response: {}", text);
        return IOUtil.writeAll(ch, payload);
    }

    private static String paramsToString(List<Param> params) {
        StringBuilder s = new StringBuilder();
        Iterator<Param> it = params.iterator();
        if (it.hasNext()) {
            Param p = it.next();
            s.append(p.getName()).append('=').append(p.getValue());
            while (it.hasNext()) {
                p = it.next();
                s.append(", ").append(p.getName()).append('=').append(p.getValue());
            }
        }
        return s.toString();
    }

    private class PolicyConnectionState implements ConnectionState {

        private static final int READ_NAME = 1;

        private static final int READ_VALUE = 2;

        private int state = READ_NAME;

        private String pendingPairName;

        private StringBuilder pendingRead = new StringBuilder();

        private List<Param> pendingRequest = new ArrayList<>();

        @Override
        public long read(SocketChannel ch, ByteBuffer buffer) throws IOException {
            long bytesRead = 0;
            while (buffer.remaining() > 0) {
                final byte c = buffer.get();
                bytesRead++;

                switch (state) {
                case READ_NAME:
                    if (c == LINE_END) {
                        handleRequest(ch, pendingRequest);
                        pendingRequest = new ArrayList<>();
                    } else if (c == '=') {
                        pendingPairName = pendingRead.toString();
                        pendingRead.setLength(0);
                        state = READ_VALUE;
                    } else {
                        pendingRead.append((char)c);
                    }
                    break;
                case READ_VALUE:
                    if (c == LINE_END) {
                        pendingRequest.add(new Param(pendingPairName, pendingRead.toString()));
                        pendingRead.setLength(0);
                        state = READ_NAME;
                    } else {
                        pendingRead.append((char)c);
                    }
                    break;
                default:
                    writePermanentError(ch, "Reached state " + state + ", but I don't know what to do...");
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
