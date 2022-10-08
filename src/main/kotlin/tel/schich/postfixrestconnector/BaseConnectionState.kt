package tel.schich.postfixrestconnector

import java.util.UUID

abstract class BaseConnectionState : ConnectionState {
    override val id: UUID = UUID.randomUUID()
}
