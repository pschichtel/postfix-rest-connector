/*
 * Postfix REST Connector - A simple TCP server that can be used as tcp lookup, socketmap lookup or policy check server for the Postfix mail server.
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
package tel.schich.postfixrestconnector.mocks;

import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

public class MockSocketChannel extends SocketChannel {

    public static final MockSocketChannel DEFAULT = new MockSocketChannel();

    public MockSocketChannel() {
        super(SelectorProvider.provider());
    }

    @Override
    public SocketChannel bind(SocketAddress local) {
        return null;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) {
        return null;
    }

    @Override
    public SocketChannel shutdownInput() {
        return null;
    }

    @Override
    public SocketChannel shutdownOutput() {
        return null;
    }

    @Override
    public Socket socket() {
        return null;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public boolean isConnectionPending() {
        return false;
    }

    @Override
    public boolean connect(SocketAddress remote) {
        return false;
    }

    @Override
    public boolean finishConnect() {
        return false;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public int read(ByteBuffer dst) {
        return 0;
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) {
        return 0;
    }

    @Override
    public int write(ByteBuffer src) {
        int remaining = src.remaining();
        src.position(src.limit());
        return remaining;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) {
        return 0;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) {
        return null;
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return null;
    }

    @Override
    protected void implCloseSelectableChannel() {

    }

    @Override
    protected void implConfigureBlocking(boolean block) {

    }
}
