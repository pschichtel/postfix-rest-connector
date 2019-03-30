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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Dsl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static tel.schich.PostfixResponse.writePermanentError;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        Configuration config;
        if (args.length == 1) {
            config = mapper.readValue(new File(args[0]), Configuration.class);
        } else {
            System.out.println("Usage: <config path>");
            System.exit(1);
            return;
        }

        final Selector selector = Selector.open();
        AtomicBoolean keepPolling = new AtomicBoolean(true);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
        byte[] javaBuffer = new byte[4096];

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            keepPolling.set(false);
            selector.wakeup();
        }));

        List<Configuration.Endpoint> endpoints = config.getEndpoints();
        Map<Integer, Configuration.Endpoint> endpointMap = new HashMap<>(endpoints.size());
        for (Configuration.Endpoint endpoint : endpoints) {
            final ServerSocketChannel serverChannel = selector.provider().openServerSocketChannel();

            InetSocketAddress bindAddress = new InetSocketAddress(endpoint.getBindAddress(), endpoint.getBindPort());
            serverChannel.bind(bindAddress);
            configureChannel(serverChannel);
            serverChannel.register(selector, OP_ACCEPT);
            endpointMap.put(getPort(bindAddress), endpoint);

            LOGGER.info("Bound to address: {}", bindAddress);

        }
        AsyncHttpClient restClient = Dsl.asyncHttpClient();

        while (keepPolling.get()) {
            int n = selector.select();
            if (n > 0) {
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();

                    SelectableChannel channel = key.channel();

                    if (key.isAcceptable() && channel instanceof ServerSocketChannel) {
                        ServerSocketChannel ch = (ServerSocketChannel) channel;
                        SocketChannel clientChannel = ch.accept();
                        configureChannel(clientChannel);
                        clientChannel.register(selector, OP_READ);
                        LOGGER.info("Incoming connection: {}", clientChannel.getRemoteAddress());
                        continue;
                    }

                    if (!channel.isOpen()) {
                        key.cancel();
                        continue;
                    }

                    if (!key.isValid()) {
                        key.cancel();
                        channel.close();
                        continue;
                    }

                    if (key.isReadable() && channel instanceof SocketChannel) {
                        SocketChannel ch = (SocketChannel) channel;
                        buffer.clear();
                        int bytesRead;
                        try {
                            bytesRead = ch.read(buffer);
                        } catch (IOException e) {
                            LOGGER.error("Channel Read failed!", e);
                            bytesRead = -1;
                        }
                        if (bytesRead == -1) {
                            ch.close();
                            key.cancel();
                            continue;
                        }

                        buffer.flip();
                        buffer.get(javaBuffer, 0, bytesRead);
                        String rawRequest = new String(javaBuffer, 0, bytesRead, US_ASCII);

                        Optional<PostfixLookupRequest> requestOpt = parseRequest(rawRequest);
                        if (requestOpt.isPresent()) {
                            PostfixLookupRequest request = requestOpt.get();
                            if (request instanceof GetLookupRequest) {

                                Configuration.Endpoint endpoint = endpointMap.get(getPort(ch.getLocalAddress()));

                                String requestKey = ((GetLookupRequest) request).getKey();
                                LOGGER.debug("Get request: {}", requestKey);
                                BoundRequestBuilder prepareRequest = restClient.preparePost(endpoint.getTarget())
                                        .setHeader("User-Agent", "Postfix REST Connector")
                                        .setHeader("X-Auth-Token", endpoint.getAuthToken());
                                ((GetLookupRequest) request)
                                        .handleRequest(ch, buffer, requestKey, mapper, prepareRequest);
                            } else {
                                writePermanentError(ch, buffer, "Unknown/unsupported request");
                            }
                        } else {
                            writePermanentError(ch, buffer, "Broken request!");
                        }
                    }

                }
            }
        }
    }

    private static void configureChannel(SelectableChannel ch) throws IOException {
        ch.configureBlocking(false);
        if (ch instanceof SocketChannel) {
            SocketChannel sch = (SocketChannel) ch;
            sch.setOption(TCP_NODELAY, true);
        }
    }

    private static Optional<PostfixLookupRequest> parseRequest(String line) {
        Matcher getMatcher = GetLookupRequest.PATTERN.matcher(line);
        if (getMatcher.matches()) {
            return Optional.of(new GetLookupRequest(decodeRequestData(getMatcher.group(1))));
        } else {
            LOGGER.warn("Unknown request: {}", line);
            return Optional.empty();
        }
    }

    private static String decodeRequestData(String data) {
        try {
            return URLDecoder.decode(data, US_ASCII.name());
        } catch (UnsupportedEncodingException e) {
            return data;
        }
    }

    private static int getPort(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        }
        return -1;
    }

}
