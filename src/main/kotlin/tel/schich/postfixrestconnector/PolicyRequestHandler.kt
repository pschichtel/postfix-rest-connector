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

import org.slf4j.LoggerFactory
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
import java.net.URLEncoder.encode as urlEncode

open class PolicyRequestHandler(
    override val endpoint: Endpoint,
    private val http: HttpClient,
    private val userAgent: String
) : PostfixRequestHandler {
    override fun createState(): ConnectionState {
        return PolicyConnectionState()
    }

    protected open fun handleRequest(ch: SocketChannel, id: UUID, params: List<Pair<String, String>>) {
        if (LOGGER.isInfoEnabled) {
            LOGGER.info("{} - Policy request on endpoint {}: {}", id, endpoint.name, formUrlEncode(params))
        }
        val uri = endpoint.target
        LOGGER.info("{} - request to: {}", id, uri)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(uri.toString()))
            .method("POST", HttpRequest.BodyPublishers.ofString(formUrlEncode(params)))
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/x-www-form-urlencoded")
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
                            writeTemporaryError(ch, id, "REST request timed out: " + err.message)
                        } else {
                            writeTemporaryError(ch, id, err.message)
                        }
                        ch.close()
                        return@whenComplete
                    }
                    val statusCode = response.statusCode()
                    LOGGER.info("{} - received response: {}", id, statusCode)
                    if (statusCode == 200) {
                        // REST call successful -> return data
                        val data = response.body()
                        if (data != null) {
                            val trimmed = data.trim { it <= ' ' }
                            LOGGER.info("{} - Response: {}", id, trimmed)
                            writeActionResponse(ch, id, trimmed)
                        } else {
                            LOGGER.warn("{} - No result!", id)
                            writeTemporaryError(ch, id, "REST result was broken!")
                        }
                    } else if (statusCode in 400..499) {
                        // REST call failed due to user error -> emit permanent error (connector is misconfigured)
                        writePermanentError(
                            ch,
                            id,
                            "REST server signaled a user error, is the connector misconfigured?"
                        )
                    } else if (statusCode in 500..599) {
                        // REST call failed due to an server err -> emit temporary error (REST server might be overloaded
                        writeTemporaryError(ch, id, "REST server had an internal error!")
                    }
                } catch (e: IOException) {
                    LOGGER.error("Failed to write response!", e)
                    try {
                        writeTemporaryError(ch, id, "REST connector encountered a problem!")
                    } catch (ex: IOException) {
                        LOGGER.error("{} - While recovering from an error failed to write response!", id, e)
                    }
                }
            }
    }

    private inner class PolicyConnectionState : BaseConnectionState() {
        private var state = STATE_READ_NAME
        private var pendingPairName: String? = null
        private var pendingRead: StringBuilder? = StringBuilder()
        private var pendingRequest: MutableList<Pair<String, String>>? = mutableListOf()
        @Throws(IOException::class)
        override fun read(ch: SocketChannel, buffer: ByteBuffer): Long {
            var bytesRead: Long = 0
            while (buffer.remaining() > 0) {
                val c = buffer.get()
                bytesRead++
                when (state) {
                    STATE_READ_NAME -> when (c) {
                        LINE_END.code.toByte() -> {
                            handleRequest(ch, id, pendingRequest ?: emptyList())
                            pendingRequest = ArrayList()
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
                        pendingRequest!!.add(Pair(pendingPairName!!, pendingRead.toString()))
                        pendingRead!!.setLength(0)
                        state = STATE_READ_NAME
                    } else {
                        pendingRead!!.append(Char(c.toUShort()))
                    }

                    else -> {
                        writePermanentError(ch, id, "Reached state $state, but I don't know what to do...")
                        ch.close()
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

    companion object {
        private const val STATE_READ_NAME = 1
        private const val STATE_READ_VALUE = 2

        private val LOGGER = LoggerFactory.getLogger(PolicyRequestHandler::class.java)
        const val MODE_NAME = "policy"
        private const val LINE_END = '\n'
        private fun formUrlEncode(params: Collection<Pair<String, String>>): String {
            return params.joinToString(separator = "&") { (key, value) ->
                urlEncode(key, StandardCharsets.US_ASCII) + "=" + urlEncode(value, StandardCharsets.US_ASCII)
            }
        }

        @Throws(IOException::class)
        private fun writePermanentError(ch: SocketChannel, id: UUID, message: String): Int {
            LOGGER.error("{} - permanent error: {}", id, message)
            return writeActionResponse(ch, id, "554 $id - $message")
        }

        @Throws(IOException::class)
        fun writeTemporaryError(ch: SocketChannel, id: UUID, message: String?): Int {
            LOGGER.warn("{} - temporary error: {}", id, message)
            return writeActionResponse(ch, id, "451 $id - $message")
        }

        @Throws(IOException::class)
        fun writeActionResponse(ch: SocketChannel, id: UUID, action: String): Int {
            val text = "action=$action$LINE_END$LINE_END"
            val payload = text.toByteArray(StandardCharsets.US_ASCII)
            LOGGER.info("{} - Response: {}", id, text)
            return writeAll(ch, payload)
        }
    }
}