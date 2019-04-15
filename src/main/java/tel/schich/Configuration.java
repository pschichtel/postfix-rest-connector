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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {
    private final String userAgent;
    private final List<Endpoint> endpoints;

    @JsonCreator
    public Configuration(@JsonProperty("user-agent") String userAgent, @JsonProperty("endpoints") List<Endpoint> endpoints) {
        this.userAgent = userAgent;
        this.endpoints = Collections.unmodifiableList(endpoints);
    }

    public String getUserAgent() {
        return userAgent;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public static final class Endpoint {
        private final String name;
        private final String target;
        private final String bindAddress;
        private final int bindPort;
        private final String authToken;
        private final int requestTimeout;

        @JsonCreator
        public Endpoint(@JsonProperty("name") String name, @JsonProperty("target") String target,
                @JsonProperty("bind-address") String bindAddress, @JsonProperty("bind-port") int bindPort,
                @JsonProperty("auth-token") String authToken, @JsonProperty("request-timeout") int requestTimeout) {
            Objects.requireNonNull(name, "name is required!");
            Objects.requireNonNull(target, "target is required!");
            Objects.requireNonNull(authToken, "auth token is required!");

            this.name = name;
            this.target = target;
            if (bindAddress == null) {
                this.bindAddress = "127.0.0.1";
            } else {
                this.bindAddress = bindAddress;
            }
            this.bindPort = bindPort;
            this.authToken = authToken;
            this.requestTimeout = requestTimeout;
        }

        public String getTarget() {
            return target;
        }

        public String getBindAddress() {
            return bindAddress;
        }

        public int getBindPort() {
            return bindPort;
        }

        public String getAuthToken() {
            return authToken;
        }

        public SocketAddress getAddress() {
            return new InetSocketAddress(getBindAddress(), getBindPort());
        }

        public int getRequestTimeout() {
            return requestTimeout;
        }

        public String getName() {
            return name;
        }
    }
}
