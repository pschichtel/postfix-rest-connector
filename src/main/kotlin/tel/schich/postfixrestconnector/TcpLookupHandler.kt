package tel.schich.postfixrestconnector

import io.github.oshai.kotlinlogging.KotlinLogging
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
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.text.Charsets.UTF_8
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {  }

@OptIn(ExperimentalUuidApi::class)
class TcpLookupHandler(
    override val endpoint: Endpoint,
    private val http: HttpClient,
) : PostfixRequestHandler {
    override fun createState(): ConnectionState = TcpConnectionState()

    suspend fun handleRequest(ch: ByteWriteChannel, id: Uuid, rawRequest: String) {
        logger.info { "$id - tcp-lookup request on endpoint ${endpoint.name}: $rawRequest" }
        if (rawRequest.length <= TcpLookup.LOOKUP_PREFIX.length || !rawRequest.startsWith(TcpLookup.LOOKUP_PREFIX)) {
            writeError(ch, id, "Broken request!")
            error("The entire tcp lookup request is shorter than the prefix length!")
        }
        val lookupKey = TcpLookup.decodeRequest(rawRequest.substring(TcpLookup.LOOKUP_PREFIX.length).trim { it <= ' ' })

        val response = try {
            http.connectorEndpointRequest(endpoint, id, logger) {
                url {
                    parameters.append("key", lookupKey)
                }
            }
        } catch (e: HttpRequestTimeoutException) {
            logger.error(e) { "$id - request timeout out!" }
            writeError(ch, id, "REST request timed out")
            return
        } catch (e: CancellationException) {
            logger.error(e) { "$id - connection coroutine got cancelled!" }
            withContext(NonCancellable) {
                writeError(ch, id, "Coroutine cancelled: " + e.message)
            }
            throw e
        } catch (e: IOException) {
            logger.error(e) { "$id - error occurred during request!" }
            writeError(ch, id, ioExceptionMessage(e))
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
                    writeSuccessfulResponse(ch, id, data, endpoint.listSeparator)
                }
            } else if (statusCode == HttpStatusCode.NotFound) {
                writeNotFoundResponse(ch, id)
            } else if (statusCode.value in 400..499) {
                // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                writeError(
                    ch,
                    id,
                    "REST server signaled a user error, is the connector misconfigured? Code: $statusCode"
                )
            } else if (statusCode.value in 500..599) {
                // REST call failed due to a server err -> emit temporary error (REST server might be overloaded
                writeError(ch, id, "REST server had an internal error: $statusCode")
            } else {
                writeError(ch, id, "REST server responded with an unspecified code: $statusCode")
            }
        } catch (e: ContentConvertException) {
            writeInvalidDataError(ch, id, response, e)
        } catch (e: NoTransformationFoundException) {
            writeInvalidDataError(ch, id, response, e)
        } catch (e: IOException) {
            logger.error(e) { "$id - Failed to write response!" }
            try {
                writeError(ch, id, "REST connector encountered a problem!")
            } catch (ex: IOException) {
                e.addSuppressed(ex)
                logger.error(e) { "$id - While recovering from an error failed to write response!" }
            }
        }
    }

    private suspend fun writeSuccessfulResponse(ch: ByteWriteChannel, id: Uuid, data: List<String>, separator: String) {
        writeResponse(ch, id, 200, encodeLookupResponse(data, separator))
    }

    private suspend fun writeNotFoundResponse(ch: ByteWriteChannel, id: Uuid) {
        writeResponse(ch, id, 500, "$id - key not found")
    }

    private suspend fun writeInvalidDataError(ch: ByteWriteChannel, id: Uuid, response: HttpResponse, e: Exception) {
        val bodyText = response.bodyAsText()
        logger.error(e) { "$id - Received invalid data with Content-Type '${response.contentType()}': $bodyText" }
        writeError(ch, id, "REST connector received invalid data!")
    }

    private suspend fun writeError(ch: ByteWriteChannel, id: Uuid, message: String?) {
        writeResponse(ch, id, 400, "$id - $message")
    }

    private suspend fun writeResponse(ch: ByteWriteChannel, id: Uuid, code: Int, data: String) {
        val text = code.toString() + ' ' + TcpLookup.encodeResponse(data) + TcpLookup.END_CHAR
        val payload = Charsets.US_ASCII.encode(text)
        if (payload.remaining() > TcpLookup.MAXIMUM_RESPONSE_LENGTH) {
            throw IOException("$id - response to long")
        }
        logger.info { "$id - Response: $text" }
        ch.writeFully(payload)
    }

    private inner class TcpConnectionState : ConnectionState() {
        private val pendingRead = ByteArrayOutputStream()

        override suspend fun read(ch: ByteWriteChannel, buffer: ByteBuffer) {
            while (buffer.remaining() > 0) {
                when (val c = buffer.get().toInt()) {
                    TcpLookup.END_CHAR_CODE -> {
                        handleRequest(ch, id, String(pendingRead.toByteArray(), UTF_8))
                        pendingRead.reset()
                    }
                    else -> {
                        pendingRead.write(c)
                    }
                }
            }
        }
    }

    companion object {
        const val MODE_NAME = "tcp-lookup"
    }
}
