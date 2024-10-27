package tel.schich.postfixrestconnector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.serialization.ContentConvertException
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.writeText
import io.ktor.utils.io.writeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {  }

@OptIn(ExperimentalUuidApi::class)
open class PolicyRequestHandler(
    override val endpoint: Endpoint,
    private val http: HttpClient,
) : PostfixRequestHandler {
    override fun createState(): ConnectionState = PolicyConnectionState()

    open suspend fun handleRequest(ch: ByteWriteChannel, id: Uuid, params: Parameters) {
        logger.info { "$id - Policy request on endpoint ${endpoint.name}: $params" }

        val response = try {
            http.connectorEndpointRequest(endpoint, id, logger) {
                method = HttpMethod.Post
                setBody(FormDataContent(params))
            }
        } catch (e: HttpRequestTimeoutException) {
            logger.error(e) { "$id - request timeout out!" }
            writeTemporaryError(ch, id, "REST request timed out")
            return
        } catch (e: CancellationException) {
            logger.error(e) { "$id - connection coroutine got cancelled!" }
            withContext(NonCancellable) {
                writeTemporaryError(ch, id, e.message ?: "unknown coroutine cancellation")
            }
            throw e
        } catch (e: IOException) {
            logger.error(e) { "$id - error occurred during request!" }
            writeTemporaryError(ch, id, ioExceptionMessage(e))
            return
        }

        try {
            val statusCode = response.status
            logger.info { "$id - received response: $statusCode" }
            if (statusCode == HttpStatusCode.OK) {
                // REST call successful -> return data
                val data = response.bodyAsText().trim { it <= ' ' }
                logger.info { "$id - Response: $data" }
                writeActionResponse(ch, id, data)
            } else if (statusCode.value in 400..499) {
                // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                writePermanentError(ch, id, "REST server signaled a user error, is the connector misconfigured?")
            } else if (statusCode.value in 500..599) {
                // REST call failed due to a server err -> temporary error (REST server might be overloaded)
                writeTemporaryError(ch, id, "REST server had an internal error!")
            }
        } catch (e: ContentConvertException) {
            writeInvalidDataError(ch, id, response, e)
        } catch (e: NoTransformationFoundException) {
            writeInvalidDataError(ch, id, response, e)
        } catch (e: IOException) {
            logger.error(e) { "$id - Failed to write response!" }
            try {
                writeTemporaryError(ch, id, "REST connector encountered a problem!")
            } catch (ex: IOException) {
                e.addSuppressed(ex)
                logger.error(e) { "$id - While recovering from an error failed to write response!" }
            }
        }
    }

    private suspend fun writePermanentError(ch: ByteWriteChannel, id: Uuid, message: String) {
        logger.error { "$id - permanent error: $message" }
        writeActionResponse(ch, id, "554 $id - $message")
    }

    private suspend fun writeTemporaryError(ch: ByteWriteChannel, id: Uuid, message: String) {
        logger.warn { "$id - temporary error: $message" }
        writeActionResponse(ch, id, "451 $id - $message")
    }

    private suspend fun writeInvalidDataError(ch: ByteWriteChannel, id: Uuid, response: HttpResponse, e: Exception) {
        val bodyText = response.bodyAsText()
        logger.error(e) { "$id - Received invalid data with Content-Type '${response.contentType()}': $bodyText" }
        writeTemporaryError(ch, id, "REST connector received invalid data!")
    }

    private suspend fun writeActionResponse(ch: ByteWriteChannel, id: Uuid, action: String) {
        val text = "action=$action$LINE_END_CHAR$LINE_END_CHAR"
        val payload = Buffer()
        payload.writeText(text, charset = Charsets.ISO_8859_1)
        logger.info { "$id - Response: $text" }
        ch.writeSource(payload)
    }

    private inner class PolicyConnectionState : ConnectionState() {
        private var state = ReadState.NAME
        private var pendingPairName: String? = null
        private val pendingRead = Buffer()
        private val pendingRequest = ParametersBuilder()

        private fun pendingReadAsString(): String {
            val string = pendingRead.readString()
            pendingRead.clear()
            return string
        }

        override suspend fun read(ch: ByteWriteChannel, byte: Byte) {
            when (state) {
                ReadState.NAME -> when (byte) {
                    LINE_END_CHAR_CODE -> {
                        handleRequest(ch, id, pendingRequest.build())
                        pendingRequest.clear()
                    }
                    VALUE_SEPARATOR_CHAR_CODE -> {
                        pendingPairName = pendingReadAsString()
                        state = ReadState.VALUE
                    }
                    else -> {
                        pendingRead.writeByte(byte)
                    }
                }

                ReadState.VALUE -> when (byte) {
                    LINE_END_CHAR_CODE -> {
                        pendingRequest.append(pendingPairName!!, pendingReadAsString())
                        state = ReadState.NAME
                    }
                    else -> {
                        pendingRead.writeByte(byte)
                    }
                }
            }
        }
    }

    companion object {
        const val MODE_NAME = "policy"
        private const val LINE_END_CHAR = '\n'
        private const val LINE_END_CHAR_CODE = LINE_END_CHAR.code.toByte()
        private const val VALUE_SEPARATOR_CHAR_CODE = '='.code.toByte()
    }

    private enum class ReadState {
        NAME,
        VALUE,
    }
}
