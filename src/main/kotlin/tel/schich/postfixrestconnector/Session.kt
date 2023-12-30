package tel.schich.postfixrestconnector

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import mu.KotlinLogging
import java.net.http.HttpClient.Version.HTTP_2

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
suspend fun startSession(config: Configuration, dispatcher: CoroutineDispatcher = Dispatchers.IO): Session {
    val selector = SelectorManager(dispatcher)
    val restClient = HttpClient(Java) {
        engine {
            protocolVersion = HTTP_2
        }
        install(HttpTimeout)
        install(ContentNegotiation) {
            json()
        }
        install(UserAgent) {
            agent = config.userAgent
        }
    }

    val job = SupervisorJob()
    val scope = CoroutineScope(job + dispatcher)
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
            else -> error("Unknown mode ${endpoint.mode}!")
        }

        val accepter = scope.launch(SupervisorJob()) {
            while (isActive) {
                val socket = listenSocket.accept()
                val actor = processConnection(endpoint, socket, handler)
                actor.invokeOnCompletion { t ->
                    if (t != null && t !is CancellationException) {
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

private fun CoroutineScope.processConnection(
    endpoint: Endpoint,
    socket: Socket,
    handler: PostfixRequestHandler
): Job {
    logger.info { "Client ${socket.remoteAddress} connected to ${socket.localAddress} (endpoint: ${endpoint.name})" }

    return launch {
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE)
        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel(autoFlush = false)
        val state = handler.createState()

        while (isActive) {
            buffer.clear()
            if (readChannel.readAvailable(buffer) == -1) {
                cancel()
                break
            }
            buffer.flip()
            state.read(writeChannel, buffer)
            writeChannel.flush()
        }
    }
}
