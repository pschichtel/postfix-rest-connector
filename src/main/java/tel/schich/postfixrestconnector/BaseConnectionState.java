package tel.schich.postfixrestconnector;

import java.util.UUID;

public abstract class BaseConnectionState implements ConnectionState {
    private final UUID id = UUID.randomUUID();

    @Override
    public final UUID getId() {
        return id;
    }
}
