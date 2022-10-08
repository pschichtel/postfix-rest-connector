package tel.schich.postfixrestconnector

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
    @SerialName("user-agent") val userAgent: String,
    @SerialName("endpoints") val endpoints: List<Endpoint>,
)
