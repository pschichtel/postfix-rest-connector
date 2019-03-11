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
import java.util.Collections;
import java.util.regex.Pattern;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.request.body.Body;
import org.asynchttpclient.request.body.generator.BodyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import static tel.schich.PostfixResponse.writePermanentError;
import static tel.schich.PostfixResponse.writeSuccessfulResponse;
import static tel.schich.PostfixResponse.writeTemporaryError;

public class GetLookupRequest implements PostfixLookupRequest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetLookupRequest.class);

    public static final Pattern PATTERN = Pattern.compile("^get\\s+(.+)\r?\n$");

    private final String key;

    public GetLookupRequest(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void handleRequest(SocketChannel ch, ByteBuffer buf, String key, ObjectMapper mapper, BoundRequestBuilder restClient) throws IOException {
        int atIndex = key.indexOf('@');

        LOGGER.info("Received key: {}", key);

        String json = mapper.writeValueAsString(new GetRequest("get", key));
        restClient.setBody(json);
        restClient.execute().toCompletableFuture().handleAsync((response, err) -> {
            try {
                if (err != null) {
                    writeTemporaryError(ch, buf, err.getMessage());
                } else {

                    int statusCode = response.getStatusCode();
                    if (statusCode == 200) {
                        // REST call successful -> return data
                        RestResponse data = mapper.readValue(response.getResponseBodyAsStream(), RestResponse.class);
                        if (data != null) {
                            return writeSuccessfulResponse(ch, buf, data.getResults());
                        } else {
                            return writeTemporaryError(ch, buf, "REST result was broken!");
                        }
                    } else if (statusCode >= 400 && statusCode < 500) {
                        // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                        writePermanentError(ch, buf,
                                "REST server signaled a user error, is the connector misconfigured?");
                    } else if (statusCode >= 500 && statusCode < 600) {
                        // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                        writeTemporaryError(ch, buf, "REST server had an internal error!");
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to write response!", e);
            }
            return null;
        });
    }

    private static final class GetRequest {
        public final String type;
        public final String key;

        public GetRequest(String type, String key) {
            this.type = type;
            this.key = key;
        }
    }
}
