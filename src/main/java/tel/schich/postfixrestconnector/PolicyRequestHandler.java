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
import java.util.List;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import static tel.schich.postfixrestconnector.PostfixProtocol.readAsciiString;
import static tel.schich.postfixrestconnector.PostfixProtocol.readToEnd;

public class PolicyRequestHandler implements PostfixRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyRequestHandler.class);
    public static final String MODE_NAME = "policy";
    private static final String END = "\n\n";

    private final Endpoint endpoint;
    private final AsyncHttpClient http;
    private final ObjectMapper json;

    public PolicyRequestHandler(Endpoint endpoint, AsyncHttpClient http, ObjectMapper json) {
        this.endpoint = endpoint;
        this.http = http;
        this.json = json;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }
    public ReadResult readRequest(ByteBuffer buf, StringBuilder out) {
        String s = readAsciiString(buf);
        boolean lastIsLineBreak = out.length() > 0 && out.charAt(out.length() - 1) == '\n';
        boolean firstIsLineBreak = s.charAt(0) == '\n';
        if (lastIsLineBreak && firstIsLineBreak) {
            if (s.length() == 1) {
                out.append(s);
                return ReadResult.COMPLETE;
            } else {
                return ReadResult.BROKEN;
            }
        }
        return readToEnd(out, s, END);
    }

    @Override
    public void handleReadError(SocketChannel ch) throws IOException {
        writeTemporaryError(ch, "received broken request");
        ch.close();
    }

    @Override
    public void handleRequest(SocketChannel ch, String rawRequest) throws IOException {
        LOGGER.info("Policy request on endpoint {}: {}", endpoint.getName(), rawRequest);

        List<Param> params = parseRequest(rawRequest);
        if (params == null) {
            writePermanentError(ch, "broken request");
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

    private static List<Param> parseRequest(String request) {
        List<Param> out = new ArrayList<>();

        int offset = 0;
        int equalIndex;
        int lineBreakIndex;
        while (request.charAt(offset) != '\n') {
            lineBreakIndex = request.indexOf('\n', offset);
            equalIndex = request.indexOf('=', offset);
            if (equalIndex == -1 || equalIndex > lineBreakIndex) {
                return null;
            }
            out.add(new Param(request.substring(offset, equalIndex),
                    request.substring(equalIndex + 1, lineBreakIndex)));
            offset = lineBreakIndex + 1;
        }

        return out;
    }


    private static int writePermanentError(SocketChannel ch, String message) throws IOException {
        return writeActionResponse(ch, "554 " + message);
    }

    public static int writeTemporaryError(SocketChannel ch, String message) throws IOException {
        return writeActionResponse(ch, "451 " + message);
    }

    public static int writeActionResponse(SocketChannel ch, String action) throws IOException {
        byte[] payload = ("action=" + action + "\n\n")
                .getBytes(StandardCharsets.US_ASCII);
        return ch.write(ByteBuffer.wrap(payload));
    }

}
