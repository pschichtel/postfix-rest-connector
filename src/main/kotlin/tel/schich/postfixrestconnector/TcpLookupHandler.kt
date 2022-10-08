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

import io.ktor.http.takeFrom
import io.ktor.server.util.url
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import tel.schich.postfixrestconnector.LookupResponseHelper.encodeResponse
import tel.schich.postfixrestconnector.LookupResponseHelper.parseResponse
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException

class TcpLookupHandler(
    override val endpoint: Endpoint,
    private val http: HttpClient,
    private val mapper: Json,
    private val userAgent: String
) : PostfixRequestHandler {
    override fun createState() = TcpConnectionState()

    @Throws(IOException::class)
    private fun handleRequest(ch: SocketChannel, id: UUID, rawRequest: String) {
        LOGGER.info("{} - tcp-lookup request on endpoint {}: {}", id, endpoint.name, rawRequest)
        if (rawRequest.length <= LOOKUP_PREFIX.length || !rawRequest.startsWith(LOOKUP_PREFIX)) {
            writeError(ch, id, "Broken request!")
            ch.close()
            return
        }
        val lookupKey = decodeURLEncodedData(rawRequest.substring(LOOKUP_PREFIX.length).trim { it <= ' ' })
        val uri = url {
            takeFrom(endpoint.target)
            parameters.append("key", lookupKey)
        }
        LOGGER.info("{} - request to: {}", id, uri)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header("User-Agent", userAgent)
            .header("X-Auth-Token", endpoint.authToken)
            .header("X-Request-Id", id.toString())
            .timeout(Duration.ofMillis(endpoint.requestTimeout.toLong()))
            .build()
        printRequest(id, request)
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { response: HttpResponse<String?>, err: Throwable? ->
                try {
                    if (err != null) {
                        LOGGER.error("{} - error occurred during request!", id, err)
                        if (err is TimeoutException) {
                            writeError(ch, id, "REST request timed out: " + err.message)
                        } else {
                            writeError(ch, id, err.message)
                        }
                        return@whenComplete
                    }
                    val statusCode = response.statusCode()
                    LOGGER.info("{} - received response: {}", id, statusCode)
                    if (statusCode == 200) {
                        // REST call successful -> return data
                        val data = response.body()
                        if (data == null) {
                            LOGGER.warn("{} - No result!", id)
                            writeError(ch, id, "REST result was broken!")
                        } else if (data.isEmpty()) {
                            writeNotFoundResponse(ch, id)
                        } else {
                            val responseValues = parseResponse(mapper, data)
                            if (responseValues.isEmpty()) {
                                writeNotFoundResponse(ch, id)
                            } else {
                                LOGGER.info("{} - Response: {}", id, responseValues)
                                writeSuccessfulResponse(ch, id, responseValues, endpoint.listSeparator)
                            }
                        }
                    } else if (statusCode == 404) {
                        writeNotFoundResponse(ch, id)
                    } else if (statusCode in 400..499) {
                        // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                        writeError(
                            ch, id,
                            "REST server signaled a user error, is the connector misconfigured? Code: $statusCode"
                        )
                    } else if (statusCode in 500..599) {
                        // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                        writeError(ch, id, "REST server had an internal error: $statusCode")
                    } else {
                        writeError(ch, id, "REST server responded with an unspecified code: $statusCode")
                    }
                } catch (e: IOException) {
                    LOGGER.error("{} - Failed to write response!", id, e)
                    try {
                        writeError(ch, id, "REST connector encountered a problem!")
                    } catch (ex: IOException) {
                        LOGGER.error("{} - While recovering from an error failed to write response!", id, e)
                    }
                }
            }
    }

    inner class TcpConnectionState : BaseConnectionState() {
        private var pendingRead: StringBuilder? = StringBuilder()

        @Throws(IOException::class)
        override fun read(ch: SocketChannel, buffer: ByteBuffer): Long {
            var bytesRead: Long = 0
            while (buffer.remaining() > 0) {
                val c = buffer.get()
                bytesRead++
                if (c == END.code.toByte()) {
                    handleRequest(ch, id, pendingRead.toString())
                    pendingRead!!.setLength(0)
                } else {
                    pendingRead!!.append(Char(c.toUShort()))
                }
            }
            return bytesRead
        }

        override fun close() {
            pendingRead = null
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(TcpLookupHandler::class.java)
        const val MODE_NAME = "tcp-lookup"
        private const val LOOKUP_PREFIX = "get "
        private const val MAXIMUM_RESPONSE_LENGTH = 4096
        private const val END = '\n'
        private const val PLUS = "+"
        private const val PLUS_URL_ENCODED = "%2B"
        private const val SPACE_URL_ENCODED = "%20"
        private fun printRequest(id: UUID, req: HttpRequest) {
            val sb = StringBuilder()
            sb.append(req.method())
            sb.append(' ')
            sb.append(req.uri().toASCIIString())
            for ((key, value1) in req.headers().map()) {
                for (value in value1) {
                    sb.append('\n')
                    sb.append(key)
                    sb.append(": ")
                    sb.append(value)
                }
            }
            sb.append("\n\n")
            req.bodyPublisher().ifPresent { sb.append("<body>") }
            LOGGER.info("Request of {}:\n{}", id, sb)
        }

        @Throws(IOException::class)
        fun writeSuccessfulResponse(ch: SocketChannel, id: UUID, data: List<String>, separator: String): Int {
            return writeResponse(ch, id, 200, encodeResponse(data, separator))
        }

        @Throws(IOException::class)
        fun writeNotFoundResponse(ch: SocketChannel, id: UUID): Int {
            return writeResponse(ch, id, 500, "$id - key not found")
        }

        @Throws(IOException::class)
        fun writeError(ch: SocketChannel, id: UUID, message: String?): Int {
            return writeResponse(ch, id, 400, "$id - $message")
        }

        @Throws(IOException::class)
        fun writeResponse(ch: SocketChannel, id: UUID, code: Int, data: String?): Int {
            val text = code.toString() + ' ' + encodeResponseData(data) + END
            val payload = text.toByteArray(StandardCharsets.US_ASCII)
            if (payload.size > MAXIMUM_RESPONSE_LENGTH) {
                throw IOException("$id - response to long")
            }
            LOGGER.info("{} - Response: {}", id, text)
            return writeAll(ch, payload)
        }

        @JvmStatic
        fun decodeURLEncodedData(data: String): String {
            return URLDecoder.decode(data.replace(PLUS, PLUS_URL_ENCODED), StandardCharsets.UTF_8)
        }

        @JvmStatic
        fun encodeResponseData(data: String?): String {
            return URLEncoder.encode(data, StandardCharsets.UTF_8)
                .replace(PLUS, SPACE_URL_ENCODED)
                .replace(PLUS_URL_ENCODED, PLUS)
        }
    }
}