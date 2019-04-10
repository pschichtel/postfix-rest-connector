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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import tel.schich.Configuration.Endpoint;

import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static tel.schich.PostfixProtocol.writePermanentError;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

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
        final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
        final AsyncHttpClient restClient = asyncHttpClient();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            keepPolling.set(false);
            selector.wakeup();
        }));

        for (Endpoint endpoint : config.getEndpoints()) {
            final ServerSocketChannel serverChannel = selector.provider().openServerSocketChannel();

            serverChannel.bind(endpoint.getAddress());
            configureChannel(serverChannel);
            serverChannel.register(selector, OP_ACCEPT);

            LOGGER.info("Bound to address: {}", serverChannel.getLocalAddress());

        }

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
                    readChannel(mapper, buffer, config, restClient, key, ch);
                }
            }
        }
    }

    private static void readChannel(ObjectMapper mapper, ByteBuffer buffer, Configuration conf, AsyncHttpClient restClient, SelectionKey key, SocketChannel ch) throws IOException {
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
            return;
        }

        buffer.flip();
        String rawRequest = readAsciiString(buffer);

        Optional<PostfixLookupRequest> requestOpt = parseRequest(rawRequest);
        if (!requestOpt.isPresent()) {
            writePermanentError(ch, buffer, "Broken request!");
            return;
        }

        PostfixLookupRequest request = requestOpt.get();
        if (!(request instanceof GetLookupRequest)) {
            writePermanentError(ch, buffer, "Unknown/unsupported request");
            return;
        }

        Endpoint endpoint = conf.getEndpoint(getPort(ch.getLocalAddress()));

        BoundRequestBuilder prepareRequest = restClient.preparePost(endpoint.getTarget())
                .setHeader("User-Agent", conf.getUserAgent())
                .setHeader("X-Auth-Token", endpoint.getAuthToken())
                .setRequestTimeout(endpoint.getRequestTimeout());

        request.handleRequest(ch, buffer, mapper, prepareRequest);
    }

    private static void configureChannel(SelectableChannel ch) throws IOException {
        ch.configureBlocking(false);
        if (ch instanceof SocketChannel) {
            SocketChannel sch = (SocketChannel) ch;
            sch.setOption(TCP_NODELAY, true);
        }
    }

    private static Optional<PostfixLookupRequest> parseRequest(String line) {
        Optional<PostfixLookupRequest> request = GetLookupRequest.parseRequest(line);
        if (!request.isPresent()) {
            LOGGER.warn("Unknown request: {}", line);
        }
        return request;
    }

    private static int getPort(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        }
        return -1;
    }

    private static String readAsciiString(ByteBuffer buf) {
        if (buf.isDirect()) {
            byte[] jbuf = new byte[buf.remaining()];
            buf.get(jbuf);
            return new String(jbuf, US_ASCII);
        } else {
            return new String(buf.array(), buf.position(), buf.remaining(), US_ASCII);
        }
    }

}
