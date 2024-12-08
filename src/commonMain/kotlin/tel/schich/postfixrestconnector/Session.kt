package tel.schich.postfixrestconnector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.Buffer

private val logger = KotlinLogging.logger("Session")

private const val READ_BUFFER_SIZE = 2048

expect fun setupHttpClient(customizer: HttpClientConfig<out HttpClientEngineConfig>.() -> Unit): HttpClient

class Session(
    private val job: Job,
    private val selector: SelectorManager,
    private val listenSockets: List<ServerSocket>,
) {
    fun bindAddresses() = listenSockets.map { it.localAddress as InetSocketAddress }

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
    val restClient = setupHttpClient {
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

internal fun iterate(buffer: ByteArray, offset: Int, length: Int): Iterator<Byte> {
    return iterator {
        var i = 0
        while (i < length) {
            yield(buffer[offset + (i++)])
        }
    }
}

private fun CoroutineScope.processConnection(
    endpoint: Endpoint,
    socket: Socket,
    handler: PostfixRequestHandler
): Job {
    logger.info { "Client ${socket.remoteAddress} connected to ${socket.localAddress} (endpoint: ${endpoint.name})" }

    return launch {
        val b = Buffer()

        val buffer = ByteArray(READ_BUFFER_SIZE)
        b.write(buffer)
        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel(autoFlush = false)
        val state = handler.createState()

        while (isActive) {
            val bytesRead = readChannel.readAvailable(buffer)
            if (bytesRead == -1) {
                cancel()
                break
            }
            try {
                for (i in 0 until bytesRead) {
                    state.read(writeChannel, buffer[i])
                }
            } finally {
                writeChannel.flush()
            }
        }
    }
}
