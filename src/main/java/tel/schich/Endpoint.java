package tel.schich;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Endpoint {
    private final String name;
    private final String target;
    private final String bindAddress;
    private final int bindPort;
    private final String authToken;
    private final int requestTimeout;
    private final String mode;

    @JsonCreator
    public Endpoint(@JsonProperty("name") String name, @JsonProperty("target") String target,
            @JsonProperty("bind-address") String bindAddress, @JsonProperty("bind-port") int bindPort,
            @JsonProperty("auth-token") String authToken, @JsonProperty("request-timeout") int requestTimeout,
            @JsonProperty("mode") String mode) {
        Objects.requireNonNull(name, "name is required!");
        Objects.requireNonNull(target, "target is required!");
        Objects.requireNonNull(authToken, "auth token is required!");
        Objects.requireNonNull(mode, "mode is required!");

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
        this.mode = mode.toLowerCase();
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

    public String getMode() {
        return mode;
    }
}
