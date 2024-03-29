package tel.schich.postfixrestconnector

import io.ktor.http.Url
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val DEFAULT_RESPONSE_VALUE_SEPARATOR = ","
const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 2000L

object UrlSerializer : KSerializer<Url> {
    override val descriptor = PrimitiveSerialDescriptor("io.ktor.http.Url", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Url) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Url = Url(decoder.decodeString())
}

@Serializable
data class Endpoint(
    @SerialName("name")
    val name: String,
    @Serializable(with = UrlSerializer::class)
    @SerialName("target")
    val target: Url,
    @SerialName("bind-address")
    val bindAddress: String,
    @SerialName("bind-port")
    val bindPort: Int,
    @SerialName("auth-token")
    val authToken: String,
    @SerialName("request-timeout")
    val requestTimeout: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    @SerialName("mode")
    val mode: String,
    @SerialName("list-separator")
    val listSeparator: String = DEFAULT_RESPONSE_VALUE_SEPARATOR,
)
