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
package tel.schich.postfixrestconnector.mocks;

import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.UUID;

import org.asynchttpclient.Dsl;
import org.asynchttpclient.Param;

import tel.schich.postfixrestconnector.Endpoint;
import tel.schich.postfixrestconnector.PolicyRequestHandler;

public class MockPolicyRequestHandler extends PolicyRequestHandler {
    private List<Param> data;

    public MockPolicyRequestHandler(Endpoint endpoint) {
        super(endpoint, Dsl.asyncHttpClient());
    }

    @Override
    protected void handleRequest(SocketChannel ch, UUID id, List<Param> params) {
        data = params;
        super.handleRequest(ch, id, params);
    }

    public List<Param> getData() {
        List<Param> copy = this.data;
        this.data = null;
        return copy;
    }

}
