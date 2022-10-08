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
package tel.schich.postfixrestconnector

import io.ktor.http.Url
import kotlinx.serialization.KSerializer
import java.net.InetSocketAddress
import java.net.SocketAddress
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val DEFAULT_RESPONSE_VALUE_SEPARATOR = ","

object UrlSerializer : KSerializer<Url> {
    override val descriptor = PrimitiveSerialDescriptor("io.ktor.http.Url", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Url) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Url = Url(decoder.decodeString())
}

@Serializable
data class Endpoint(@SerialName("name") val name: String,
                    @Serializable(with = UrlSerializer::class)
                    @SerialName("target") val target: Url,
                    @SerialName("bind-address") val bindAddress: String,
                    @SerialName("bind-port") val bindPort: Int,
                    @SerialName("auth-token") val authToken: String,
                    @SerialName("request-timeout") val requestTimeout: Int,
                    @SerialName("mode") val mode: String,
                    @SerialName("list-separator") val listSeparator: String = DEFAULT_RESPONSE_VALUE_SEPARATOR,
) {
    fun address(): SocketAddress = InetSocketAddress(bindAddress, bindPort)
}