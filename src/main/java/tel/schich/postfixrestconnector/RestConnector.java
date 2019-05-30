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

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static org.asynchttpclient.Dsl.asyncHttpClient;

public class RestConnector implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestConnector.class);
    private static final int READ_BUFFER_SIZE = 2048;

    private volatile Selector selector;
    private volatile boolean keepPolling = true;

    public void start(SelectorProvider provider, Configuration config) throws IOException {
        start(provider, new ObjectMapper(), config);
    }

    public void start(SelectorProvider provider, Path configPath) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final Configuration config = mapper.readValue(configPath.toFile(), Configuration.class);

        start(provider, mapper, config);
    }

    private void start(SelectorProvider provider, ObjectMapper mapper, Configuration config) throws IOException {

        selector = provider.openSelector();
        final ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
        final AsyncHttpClient restClient = getConfiguredClient(config);


        for (Endpoint endpoint : config.getEndpoints()) {
            final ServerSocketChannel serverChannel = provider.openServerSocketChannel();

            final PostfixRequestHandler request;
            switch (endpoint.getMode()) {
            case TcpLookupHandler.MODE_NAME:
                request = new TcpLookupHandler(endpoint, restClient, mapper);
                break;
            case SocketmapLookupHandler.MODE_NAME:
                request = new SocketmapLookupHandler(endpoint, restClient, mapper);
                break;
            case PolicyRequestHandler.MODE_NAME:
                request = new PolicyRequestHandler(endpoint, restClient);
                break;
            default:
                throw new IllegalArgumentException("Unknown mode " + endpoint.getMode() + "!");
            }

            serverChannel.bind(endpoint.getAddress());
            configureChannel(serverChannel);
            serverChannel.register(selector, OP_ACCEPT, request);

            LOGGER.info("Bound endpoint {} to address: {}", endpoint.getName(), serverChannel.getLocalAddress());
        }

        Map<Channel, ConnectionState> connectionState = new HashMap<>();

        while (keepPolling) {
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
                    PostfixRequestHandler handler = (PostfixRequestHandler) key.attachment();
                    Endpoint endpoint = handler.getEndpoint();
                    clientChannel.register(selector, OP_READ, handler);
                    connectionState.put(clientChannel, handler.createState());
                    SocketAddress remoteAddress = clientChannel.getRemoteAddress();
                    LOGGER.info("Incoming connection from {} on endpoint {}", remoteAddress, endpoint.getName());
                    continue;
                }

                if (!key.isValid()) {
                    channel.close();
                }

                if (!channel.isOpen()) {
                    key.cancel();
                    connectionState.remove(channel);
                    continue;
                }

                if (key.isReadable() && channel instanceof SocketChannel) {
                    SocketChannel ch = (SocketChannel) channel;
                    PostfixRequestHandler handler = (PostfixRequestHandler) key.attachment();
                    ConnectionState state = connectionState.get(ch);
                    readChannel(ch, buffer, state);
                }
            }
        }
    }

    public void stop() {
        if (selector != null) {
            this.keepPolling = false;
            selector.wakeup();
        }
    }

    @Override
    public void close() {
        this.stop();
    }

    private static void readChannel(SocketChannel ch, ByteBuffer buffer,  ConnectionState state) throws IOException {
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
        state.read(ch, buffer);
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
