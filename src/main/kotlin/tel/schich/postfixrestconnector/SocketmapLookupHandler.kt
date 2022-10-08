package tel.schich.postfixrestconnector

import io.ktor.http.takeFrom
import io.ktor.server.util.url
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import tel.schich.postfixrestconnector.LookupResponseHelper.encodeResponse
import tel.schich.postfixrestconnector.LookupResponseHelper.parseResponse
import tel.schich.postfixrestconnector.Netstring.compileOne
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException

open class SocketmapLookupHandler(
    override val endpoint: Endpoint,
    private val http: HttpClient,
    private val mapper: Json,
    private val userAgent: String
) : PostfixRequestHandler {

    override fun createState(): ConnectionState {
        return SocketMapConnectionState()
    }

    @Throws(IOException::class)
    protected open fun handleRequest(ch: SocketChannel, id: UUID, requestData: String) {
        LOGGER.info("{} - socketmap-lookup request on endpoint {}: {}", id, endpoint.name, requestData)
        val spacePos = requestData.indexOf(' ')
        if (spacePos == -1) {
            writeBrokenRequestErrorAndClose(ch, id, "invalid request format")
            return
        }

        // splitting at the first space assumes, that map names with spaces cannot be configured in postfix
        // compared to the TCP lookup, these values are not URL encoded
        val name = requestData.substring(0, spacePos)
        val lookupKey = requestData.substring(spacePos + 1)
        val uri = url {
            takeFrom(endpoint.target)
            parameters.append("name", name)
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
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { response: HttpResponse<String?>, err: Throwable? ->
                try {
                    if (err != null) {
                        LOGGER.error("{} - error occurred during request!", id, err)
                        if (err is TimeoutException) {
                            writeTimeoutError(ch, id, "REST request timed out: " + err.message)
                        } else {
                            writeTempError(ch, id, err.message)
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
                            writeTempError(ch, id, "REST result was broken!")
                        } else if (data.isEmpty()) {
                            writeNotFoundResponse(ch, id)
                        } else {
                            val responseValues: List<String> = parseResponse(mapper, data)
                            if (responseValues.isEmpty()) {
                                writeNotFoundResponse(ch, id)
                            } else {
                                LOGGER.info("{} - Response: {}", id, responseValues)
                                writeOkResponse(ch, id, responseValues, endpoint.listSeparator)
                            }
                        }
                    } else if (statusCode == 404) {
                        writeNotFoundResponse(ch, id)
                    } else if (statusCode in 400..499) {
                        // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                        writePermError(
                            ch, id,
                            "REST server signaled a user error, is the connector misconfigured? Code: $statusCode"
                        )
                    } else if (statusCode in 500..599) {
                        // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                        writeTempError(ch, id, "REST server had an internal error: $statusCode")
                    } else {
                        writeTempError(ch, id, "REST server responded with an unspecified code: $statusCode")
                    }
                } catch (e: IOException) {
                    LOGGER.error("{} - Failed to write response!", id, e)
                    try {
                        writeTempError(ch, id, "REST connector encountered a problem!")
                    } catch (ex: IOException) {
                        e.addSuppressed(ex)
                        LOGGER.error("{} - While recovering from an error failed to write response!", id, e)
                    }
                }
            }
    }

    private inner class SocketMapConnectionState : BaseConnectionState() {
        private var state = STATE_READ_LENGTH
        private var length: Long = 0
        private var pendingRead: StringBuilder? = StringBuilder()

        @Throws(IOException::class)
        override fun read(ch: SocketChannel, buffer: ByteBuffer): Long {
            var bytesRead: Long = 0
            while (buffer.remaining() > 0) {
                val c = buffer.get()
                bytesRead++
                when (state) {
                    STATE_READ_LENGTH -> if (c == ':'.code.toByte()) {
                        state = STATE_READ_VALUE
                        pendingRead!!.setLength(0)
                    } else {
                        val digit = c - '0'.code.toByte()
                        if (digit < 0 || digit > 9) {
                            writeBrokenRequestErrorAndClose(
                                ch,
                                id,
                                "Expected a digit, but got: " + Char(c.toUShort())
                            )
                        }
                        length = length * 10 + digit
                    }

                    STATE_READ_VALUE -> {
                        if (pendingRead!!.length < length) {
                            pendingRead!!.append(Char(c.toUShort()))
                        }
                        if (pendingRead!!.length >= length) {
                            state = STATE_READ_END
                        }
                    }

                    STATE_READ_END -> if (c == END.code.toByte()) {
                        state = STATE_READ_LENGTH
                        length = 0
                        handleRequest(ch, id, pendingRead.toString())
                    } else {
                        writeBrokenRequestErrorAndClose(ch, id, "Expected comma, but got: " + Char(c.toUShort()))
                    }

                    else -> writeBrokenRequestErrorAndClose(
                        ch,
                        id,
                        "Reached state $state, but I don't know what to do..."
                    )
                }
            }
            return bytesRead
        }

        override fun close() {
            pendingRead = null
        }
    }

    companion object {
        private const val STATE_READ_LENGTH = 1
        private const val STATE_READ_VALUE = 2
        private const val STATE_READ_END = 3

        private val LOGGER = LoggerFactory.getLogger(SocketmapLookupHandler::class.java)
        const val MODE_NAME = "socketmap-lookup"
        private const val MAXIMUM_RESPONSE_LENGTH = 10000
        private const val END = ','

        @Throws(IOException::class)
        fun writeOkResponse(ch: SocketChannel, id: UUID, data: List<String>, separator: String): Int {
            return writeResponse(ch, id, "OK " + encodeResponse(data, separator))
        }

        @Throws(IOException::class)
        fun writeNotFoundResponse(ch: SocketChannel, id: UUID): Int {
            return writeResponse(ch, id, "NOTFOUND ")
        }

        @Throws(IOException::class)
        fun writeBrokenRequestErrorAndClose(ch: SocketChannel, id: UUID, reason: String) {
            LOGGER.error("{} - broken request: {}", id, reason)
            writePermError(ch, id, "Broken request! ($reason)")
            ch.close()
        }

        @Throws(IOException::class)
        fun writeTimeoutError(ch: SocketChannel, id: UUID, message: String): Int {
            return writeResponse(ch, id, "TIMEOUT $id - $message")
        }

        @Throws(IOException::class)
        fun writeTempError(ch: SocketChannel, id: UUID, message: String?): Int {
            return writeResponse(ch, id, "TEMP $id - $message")
        }

        @Throws(IOException::class)
        fun writePermError(ch: SocketChannel, id: UUID, message: String): Int {
            return writeResponse(ch, id, "PERM $id - $message")
        }

        @Throws(IOException::class)
        fun writeResponse(ch: SocketChannel, id: UUID, data: String): Int {
            if (data.length > MAXIMUM_RESPONSE_LENGTH) {
                throw IOException("$id - response to long")
            }
            val text = compileOne(data)
            LOGGER.info("{} - Response: {}", id, text)
            val payload = text.toByteArray(StandardCharsets.US_ASCII)
            return writeAll(ch, payload)
        }
    }
}
