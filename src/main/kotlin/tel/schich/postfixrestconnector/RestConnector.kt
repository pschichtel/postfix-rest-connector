package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.tcpNoDelay
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import mu.KotlinLogging

private val logger = KotlinLogging.logger {  }

private const val READ_BUFFER_SIZE = 2048

class Session(
    private val job: Job,
    private val selector: SelectorManager,
    private val listenSockets: List<ServerSocket>,
) {
    suspend fun join() {
        job.join()
    }

    fun close() {
        job.cancel()
        for (listenSocket in listenSockets) {
            listenSocket.close()
        }
        selector.close()
    }
}

class RestConnector {

    suspend fun start(config: Configuration): Session {
        val selector = SelectorManager(Dispatchers.IO)
        val restClient = HttpClient(CIO) {
            install(HttpTimeout)
            install(ContentNegotiation) {
                json()
            }
            install(UserAgent) {
                agent = config.userAgent
            }
        }

        val job = SupervisorJob()
        val scope = CoroutineScope(job)
        val sockets = config.endpoints.map { endpoint ->
            val listenSocket = aSocket(selector)
                .tcp()
                .tcpNoDelay()
                .bind(InetSocketAddress(endpoint.bindAddress, endpoint.bindPort))

            logger.info { "Bound endpoint ${endpoint.name} to address: ${listenSocket.localAddress}" }

            val handler = when (endpoint.mode) {
                TcpLookupHandler.MODE_NAME -> TcpLookupHandler(endpoint, restClient)
                SocketmapLookupHandler.MODE_NAME -> SocketmapLookupHandler(endpoint, restClient)
                PolicyRequestHandler.MODE_NAME -> PolicyRequestHandler(endpoint, restClient)
                else -> error("Unknown mode " + endpoint.mode + "!")
            }

            val accepter = scope.launch(SupervisorJob()) {
                while (isActive) {
                    val socket = listenSocket.accept()
                    val actor = processConnection(endpoint, socket, handler)
                    actor.invokeOnCompletion { t ->
                        if (t != null) {
                            logger.error(t) { "Client socket $socket closed due to error while processing!" }
                        }
                        socket.close()
                    }
                }
            }

            accepter.invokeOnCompletion { t ->
                if (t != null) {
                    logger.error(t) { "Listen socket $listenSocket closed due to error while accepting!" }
                }
                listenSocket.close()
            }

            listenSocket
        }

        return Session(job, selector, sockets)
    }

    private suspend fun processConnection(endpoint: Endpoint, socket: Socket, handler: PostfixRequestHandler): Job {
        logger.info { "Client ${socket.remoteAddress} connected to ${socket.localAddress} (endpoint: ${endpoint.name})" }

        val buffer: ByteBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE)
        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel()
        val state = handler.createState()

        return coroutineScope {
            launch {
                try {
                    while (isActive) {
                        buffer.clear()
                        if (readChannel.readAvailable(buffer) == -1) {
                            cancel()
                            break
                        }
                        buffer.flip()
                        state.read(writeChannel, buffer)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Connection actor failed!" }
                }
            }
        }
    }
}
