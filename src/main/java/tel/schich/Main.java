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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static org.asynchttpclient.Dsl.asyncHttpClient;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final int READ_BUFFER_SIZE = 8196;

    public static void main(String[] args) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();

        final Configuration config;
        if (args.length == 1) {
            config = mapper.readValue(new File(args[0]), Configuration.class);
        } else {
            System.out.println("Usage: <config path>");
            System.exit(1);
            return;
        }

        final Selector selector = Selector.open();
        final AtomicBoolean keepPolling = new AtomicBoolean(true);
        final ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
        final AsyncHttpClient restClient = getConfiguredClient(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            keepPolling.set(false);
            selector.wakeup();
        }));

        for (Endpoint endpoint : config.getEndpoints()) {
            final ServerSocketChannel serverChannel = selector.provider().openServerSocketChannel();

            final PostfixRequestHandler request;
            switch (endpoint.getMode()) {
            case LookupRequestHandler.MODE_NAME:
                request = new LookupRequestHandler(endpoint, restClient);
                break;
            case PolicyRequestHandler.MODE_NAME:
                request = new PolicyRequestHandler(endpoint, restClient, mapper);
                break;
            default:
                throw new IllegalArgumentException("Unknown mode " + endpoint.getMode() + "!");
            }

            serverChannel.bind(endpoint.getAddress());
            configureChannel(serverChannel);
            serverChannel.register(selector, OP_ACCEPT, request);

            LOGGER.info("Bound endpoint {} to address: {}", endpoint.getName(), serverChannel.getLocalAddress());

        }

        Map<Channel, StringBuilder> pendingRequests = new HashMap<>();

        while (keepPolling.get()) {
            if (selector.select() <= 0) {
                continue;
            }

            final Set<SelectionKey> keys = selector.selectedKeys();
            final Iterator<SelectionKey> it = keys.iterator();
            while (it.hasNext()) {
                final SelectionKey key = it.next();
                it.remove();

                final SelectableChannel channel = key.channel();

                if (key.isAcceptable() && channel instanceof ServerSocketChannel) {
                    ServerSocketChannel ch = (ServerSocketChannel) channel;
                    SocketChannel clientChannel = ch.accept();
                    configureChannel(clientChannel);
                    PostfixRequestHandler request = (PostfixRequestHandler) key.attachment();
                    Endpoint endpoint = request.getEndpoint();
                    clientChannel.register(selector, OP_READ, request);
                    SocketAddress remoteAddress = clientChannel.getRemoteAddress();
                    LOGGER.info("Incoming connection from {} on endpoint {}", remoteAddress, endpoint.getName());
                    continue;
                }

                if (!channel.isOpen()) {
                    key.cancel();
                    pendingRequests.remove(channel);
                    continue;
                }

                if (!key.isValid()) {
                    channel.close();
                    key.cancel();
                    pendingRequests.remove(channel);
                    continue;
                }

                if (key.isReadable() && channel instanceof SocketChannel) {
                    SocketChannel ch = (SocketChannel) channel;
                    PostfixRequestHandler handler = (PostfixRequestHandler) key.attachment();
                    readChannel(ch, buffer, handler, pendingRequests);
                }
            }
        }
    }

    private static void readChannel(SocketChannel ch, ByteBuffer buffer, PostfixRequestHandler handler,  Map<Channel, StringBuilder> pendingRequests) throws IOException {
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
            return;
        }

        buffer.flip();
        StringBuilder builder = pendingRequests.computeIfAbsent(ch, c -> new StringBuilder());

        switch (handler.readRequest(buffer, builder)) {
        case BROKEN:
            handler.handleReadError(ch);
            return;
        case PENDING:
            return;
        case COMPLETE:
            pendingRequests.remove(ch);
            handler.handleRequest(ch, builder.toString());
        }
    }

    private static void configureChannel(SelectableChannel ch) throws IOException {
        ch.configureBlocking(false);
        if (ch instanceof SocketChannel) {
            SocketChannel sch = (SocketChannel) ch;
            sch.setOption(TCP_NODELAY, true);
        }
    }

    private static AsyncHttpClient getConfiguredClient(Configuration config) {
        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder()
                .setUserAgent(config.getUserAgent());

        return asyncHttpClient(builder);
    }

}
