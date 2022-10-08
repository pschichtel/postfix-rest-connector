package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID

private val logger = KotlinLogging.logger {  }

open class PolicyRequestHandler(
    override val endpoint: Endpoint,
    private val http: HttpClient,
    private val userAgent: String
) : PostfixRequestHandler {
    override fun createState(): ConnectionState {
        return PolicyConnectionState()
    }

    protected open suspend fun handleRequest(ch: ByteWriteChannel, id: UUID, params: Parameters) {
        logger.info { "$id - Policy request on endpoint ${endpoint.name}: $params" }

        val response = try {
            http.request(endpoint.target) {
                method = HttpMethod.Post
                logger.info { "$id - request to: $url" }
                headers.append("User-Agent", userAgent)
                headers.append("X-Auth-Token", endpoint.authToken)
                headers.append("X-Request-Id", id.toString())
                setBody(FormDataContent(params))
                timeout {
                    requestTimeoutMillis = endpoint.requestTimeout.toLong()
                }
                logRequest(id, logger)
            }
        } catch (e: CancellationException) {
            logger.error(e) { "$id - error occurred during request!" }
            withContext(NonCancellable) {
                if (e is TimeoutCancellationException) {
                    writeTemporaryError(ch, id, "REST request timed out: " + e.message)
                } else {
                    writeTemporaryError(ch, id, e.message)
                }
                ch.close(e)
            }
            throw e
        } catch (e: IOException) {
            logger.error(e) { "$id - error occurred during request!" }
            writeTemporaryError(ch, id, e.message)
            throw e
        }

        try {
            val statusCode = response.status
            logger.info { "$id - received response: $statusCode" }
            if (statusCode == HttpStatusCode.OK) {
                // REST call successful -> return data
                val data = response.bodyAsText()
                if (data != null) {
                    val trimmed = data.trim { it <= ' ' }
                    logger.info { "$id - Response: $trimmed" }
                    writeActionResponse(ch, id, trimmed)
                } else {
                    logger.warn { "$id - No result!" }
                    writeTemporaryError(ch, id, "REST result was broken!")
                }
            } else if (statusCode.value in 400..499) {
                // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                writePermanentError(ch, id, "REST server signaled a user error, is the connector misconfigured?")
            } else if (statusCode.value in 500..599) {
                // REST call failed due to an server err -> temporary error (REST server might be overloaded)
                writeTemporaryError(ch, id, "REST server had an internal error!")
            }
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

    private inner class PolicyConnectionState : BaseConnectionState() {
        private var state = STATE_READ_NAME
        private var pendingPairName: String? = null
        private var pendingRead: StringBuilder? = StringBuilder()
        private var pendingRequest: ParametersBuilder? = ParametersBuilder()

        override suspend fun read(ch: ByteWriteChannel, buffer: ByteBuffer): Long {
            var bytesRead: Long = 0
            while (buffer.remaining() > 0) {
                val c = buffer.get()
                bytesRead++
                when (state) {
                    STATE_READ_NAME -> when (c) {
                        LINE_END.code.toByte() -> {
                            handleRequest(ch, id, pendingRequest?.build() ?: Parameters.Empty)
                            pendingRequest = ParametersBuilder()
                        }
                        '='.code.toByte() -> {
                            pendingPairName = pendingRead.toString()
                            pendingRead!!.setLength(0)
                            state = STATE_READ_VALUE
                        }
                        else -> {
                            pendingRead!!.append(Char(c.toUShort()))
                        }
                    }

                    STATE_READ_VALUE -> if (c == LINE_END.code.toByte()) {
                        pendingRequest!!.append(pendingPairName!!, pendingRead.toString())
                        pendingRead!!.setLength(0)
                        state = STATE_READ_NAME
                    } else {
                        pendingRead!!.append(Char(c.toUShort()))
                    }

                    else -> {
                        writePermanentError(ch, id, "Reached state $state, but I don't know what to do...")
                        ch.close(cause = null)
                    }
                }
            }
            return bytesRead
        }

        override fun close() {
            pendingRead = null
            pendingRequest = null
        }
    }

    private suspend fun writePermanentError(ch: ByteWriteChannel, id: UUID, message: String) {
        logger.error { "$id - permanent error: $message" }
        writeActionResponse(ch, id, "554 $id - $message")
    }

    private suspend fun writeTemporaryError(ch: ByteWriteChannel, id: UUID, message: String?) {
        logger.warn { "$id - temporary error: $message" }
        writeActionResponse(ch, id, "451 $id - $message")
    }

    private suspend fun writeActionResponse(ch: ByteWriteChannel, id: UUID, action: String) {
        val text = "action=$action$LINE_END$LINE_END"
        val payload = Charsets.US_ASCII.encode(text)
        logger.info { "$id - Response: $text" }
        ch.writeFully(payload)
        ch.flush()
    }

    companion object {
        private const val STATE_READ_NAME = 1
        private const val STATE_READ_VALUE = 2

        const val MODE_NAME = "policy"
        private const val LINE_END = '\n'
    }
}
