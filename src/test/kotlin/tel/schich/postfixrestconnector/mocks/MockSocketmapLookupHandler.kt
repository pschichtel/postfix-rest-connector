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

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import kotlinx.serialization.json.Json;
import org.jetbrains.annotations.NotNull;
import tel.schich.postfixrestconnector.Endpoint;
import tel.schich.postfixrestconnector.SocketmapLookupHandler;

public class MockSocketmapLookupHandler extends SocketmapLookupHandler {
    private String data;

    public MockSocketmapLookupHandler(Endpoint endpoint) {
        super(endpoint, HttpClient.newHttpClient(), Json.Default, "test");
    }

    @Override
    protected void handleRequest(@NotNull SocketChannel ch, @NotNull UUID id, @NotNull String requestData) throws IOException {
        data = requestData;
        super.handleRequest(ch, id, requestData);
    }

    public String getData() {
        return data;
    }
}
