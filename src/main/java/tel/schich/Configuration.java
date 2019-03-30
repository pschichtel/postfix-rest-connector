package tel.schich;

import java.util.Collections;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {
    private final List<Endpoint> endpoints;

    @JsonCreator
    public Configuration(@JsonProperty("endpoints") List<Endpoint> endpoints) {
        this.endpoints = Collections.unmodifiableList(endpoints);
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public static final class Endpoint {
        private final String target;
        private final String bindAddress;
        private final int bindPort;
        private final String authToken;

        @JsonCreator
        public Endpoint(@JsonProperty("target") String target, @JsonProperty("bind-address") String bindAddress,
                @JsonProperty("bind-port") int bindPort, @JsonProperty("auth-token") String authToken) {

            if (target == null) {
                throw new IllegalArgumentException("target is required!");
            }

            if (authToken == null) {
                throw new IllegalArgumentException("auth token is required!");
            }

            this.target = target;
            if (bindAddress == null) {
                this.bindAddress = "127.0.0.1";
            } else {
                this.bindAddress = bindAddress;
            }
            this.bindPort = bindPort;
            this.authToken = authToken;
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
    }
}
