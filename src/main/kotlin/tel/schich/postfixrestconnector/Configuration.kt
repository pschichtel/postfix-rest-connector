package tel.schich.postfixrestconnector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val DEFAULT_USER_AGENT = "Postfix REST Connector"

@Serializable
data class Configuration(
    @SerialName("user-agent") val userAgent: String = DEFAULT_USER_AGENT,
    @SerialName("endpoints") val endpoints: List<Endpoint>,
)
