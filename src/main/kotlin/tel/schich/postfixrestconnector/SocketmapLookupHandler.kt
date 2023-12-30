package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import tel.schich.postfixrestconnector.Netstring.compileOne
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.text.Charsets.UTF_8

private val logger = KotlinLogging.logger {  }

open class SocketmapLookupHandler(
    override val endpoint: Endpoint,
    private val http: HttpClient,
) : PostfixRequestHandler {

    override fun createState(): ConnectionState = SocketMapConnectionState()

    open suspend fun handleRequest(ch: ByteWriteChannel, id: UUID, requestData: String) {
        logger.info { "$id - socketmap-lookup request on endpoint ${endpoint.name}: $requestData" }
        val spacePos = requestData.indexOf(' ')
        if (spacePos == -1) {
            writeBrokenRequestErrorAndClose(ch, id, "invalid request format")
            return
        }

        // splitting at the first space assumes, that map names with spaces cannot be configured in postfix
        // compared to the TCP lookup, these values are not URL encoded
        val name = requestData.substring(0, spacePos)
        val lookupKey = requestData.substring(spacePos + 1)

        val response = try {
            http.connectorEndpointRequest(endpoint, id, logger) {
                url {
                    parameters.append("name", name)
                    parameters.append("key", lookupKey)
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            logger.error(e) { "$id - request timeout out!" }
            writeTimeoutError(ch, id, "REST request timed out")
            return
        } catch (e: CancellationException) {
            logger.error(e) { "$id - connection coroutine got cancelled!" }
            withContext(NonCancellable) {
                writeTempError(ch, id, e.message)
            }
            throw e
        } catch (e: IOException) {
            logger.error(e) { "$id - error occurred during request!" }
            writeTempError(ch, id, ioExceptionMessage(e))
            return
        }

        try {
            val statusCode = response.status
            logger.info { "$id - received response: $statusCode" }
            if (statusCode == HttpStatusCode.OK) {
                // REST call successful -> return data
                val data = response.body<List<String>>()
                if (data.isEmpty()) {
                    writeNotFoundResponse(ch, id)
                } else {
                    logger.info { "$id - Response: $data" }
                    writeOkResponse(ch, id, data, endpoint.listSeparator)
                }
            } else if (statusCode == HttpStatusCode.NotFound) {
                writeNotFoundResponse(ch, id)
            } else if (statusCode.value in 400..499) {
                // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                writePermError(ch, id, "REST server signaled a user error, is the connector misconfigured? Code: $statusCode")
            } else if (statusCode.value in 500..599) {
                // REST call failed due to a server err -> emit temporary error (REST server might be overloaded
                writeTempError(ch, id, "REST server had an internal error: $statusCode")
            } else {
                writeTempError(ch, id, "REST server responded with an unspecified code: $statusCode")
            }
        } catch (e: ContentConvertException) {
            writeInvalidDataError(ch, id, response, e)
        } catch (e: NoTransformationFoundException) {
            writeInvalidDataError(ch, id, response, e)
        } catch (e: IOException) {
            logger.error(e) { "$id - Failed to write response!" }
            try {
                writeTempError(ch, id, "REST connector encountered a problem!")
            } catch (ex: IOException) {
                e.addSuppressed(ex)
                logger.error(e) { "$id - While recovering from an error failed to write response!" }
            }
        }
    }

    private suspend fun writeOkResponse(ch: ByteWriteChannel, id: UUID, data: List<String>, separator: String) {
        writeResponse(ch, id, "OK " + encodeLookupResponse(data, separator))
    }

    private suspend fun writeNotFoundResponse(ch: ByteWriteChannel, id: UUID) {
        writeResponse(ch, id, "NOTFOUND ")
    }

    private suspend fun writeBrokenRequestErrorAndClose(ch: ByteWriteChannel, id: UUID, reason: String) {
        logger.error { "$id - broken request: $reason" }
        writePermError(ch, id, "Broken request! ($reason)")
        error(reason)
    }

    private suspend fun writeTimeoutError(ch: ByteWriteChannel, id: UUID, message: String) {
        writeResponse(ch, id, "TIMEOUT $id - $message")
    }

    private suspend fun writeTempError(ch: ByteWriteChannel, id: UUID, message: String?) {
        writeResponse(ch, id, "TEMP $id - $message")
    }

    private suspend fun writePermError(ch: ByteWriteChannel, id: UUID, message: String) {
        writeResponse(ch, id, "PERM $id - $message")
    }

    private suspend fun writeInvalidDataError(ch: ByteWriteChannel, id: UUID, response: HttpResponse, e: Exception) {
        val bodyText = response.bodyAsText()
        logger.error(e) { "$id - Received invalid data with Content-Type '${response.contentType()}': $bodyText" }
        writeTempError(ch, id, "REST connector received invalid data!")
    }

    private suspend fun writeResponse(ch: ByteWriteChannel, id: UUID, data: String) {
        if (data.length > MAXIMUM_RESPONSE_LENGTH) {
            throw IOException("$id - response to long")
        }
        val text = compileOne(data)
        logger.info { "$id - Response: $text" }
        val payload = Charsets.US_ASCII.encode(text)
        ch.writeFully(payload)
    }

    private inner class SocketMapConnectionState : ConnectionState() {
        private var state = ReadState.LENGTH
        private var length = 0L
        private val pendingRead = ByteArrayOutputStream()

        override suspend fun read(ch: ByteWriteChannel, buffer: ByteBuffer) {
            while (buffer.remaining() > 0) {
                val c = buffer.get().toInt()
                when (state) {
                    ReadState.LENGTH -> when (c) {
                        LENGTH_VALUE_SEPARATOR_CHAR_CODE -> {
                            state = ReadState.VALUE
                        }
                        else -> {
                            val digit = c - '0'.code
                            if (digit < 0 || digit > 9) {
                                writeBrokenRequestErrorAndClose(ch, id, "Expected a digit, but got: ${c.toChar()} (code: $c)")
                            }
                            length = length * 10 + digit
                        }
                    }
                    ReadState.VALUE -> {
                        if (pendingRead.size() < length) {
                            pendingRead.write(c)
                        }
                        if (pendingRead.size() >= length) {
                            state = ReadState.END
                        }
                    }
                    ReadState.END -> when (c) {
                        END_CHAR_CODE -> {
                            state = ReadState.LENGTH
                            length = 0
                            handleRequest(ch, id, String(pendingRead.toByteArray(), UTF_8))
                            pendingRead.reset()
                        }
                        else -> {
                            writeBrokenRequestErrorAndClose(ch, id, "Expected comma, but got: ${c.toChar()} (code: $c)")
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val MODE_NAME = "socketmap-lookup"
        private const val MAXIMUM_RESPONSE_LENGTH = 10000
        private const val END_CHAR_CODE = ','.code
        private const val LENGTH_VALUE_SEPARATOR_CHAR_CODE = ':'.code
    }

    private enum class ReadState {
        LENGTH,
        VALUE,
        END,
    }
}
