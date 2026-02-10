package tel.schich.postfixrestconnector

import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class Msg(val msg: Any, val typeInfo: TypeInfo)
data class Res(val status: HttpStatusCode, val message: Msg?)
data class Req(val call: RoutingCall, val body: String?, val response: CompletableDeferred<Res>)

@OptIn(ExperimentalUuidApi::class)
private fun randomString(): String = Uuid.random().toString()

class TestContext(
    val readChannel: ByteReadChannel,
    val writeChannel: ByteWriteChannel,
    private val calls: Channel<Req>,
    val endpoint: Endpoint,
) {
    suspend fun receiveReq() = calls.receive()

    suspend fun write(s: String) {
        writeChannel.writeStringUtf8(s)
        writeChannel.flush()
    }

    suspend fun writeln() {
        write("\n")
    }

    suspend fun writeln(s: String) {
        write(s + "\n")
    }

    suspend fun readln(): String? {
        return readChannel.readLine()
    }

    suspend fun readAvailable(): String? {
        val buf = ByteArray(12000)
        val bytesRead = readChannel.readAvailable(buf)
        if (bytesRead == 0) {
            return null
        }
        return buf.decodeToString(0, bytesRead)
    }

    inline infix fun <reified T : Any> HttpStatusCode.with(msg: T?): Res {
        return if (msg == null) {
            Res(this, null)
        } else {
            Res(this, Msg(msg, typeInfo<T>()))
        }
    }
}

fun systemTest(mode: String, block: suspend TestContext.() -> Unit) {
    val name = randomString()
    val targetPath = "/$name"
    val calls = Channel<Req>()

    fun RoutingContext.assertHeaders() {
        assertEquals(name, call.request.headers["x-auth-token"])
        assertNotNull(call.request.headers["x-request-id"])
    }

    suspend fun RoutingContext.processes(body: String? = null) {
        assertHeaders()
        val response = CompletableDeferred<Res>()
        calls.send(Req(call, body, response))
        val (status, msg) = response.await()
        call.response.status(status)
        if (msg != null) {
            call.respond(msg.msg, msg.typeInfo)
        }
    }

    val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
        install(ContentNegotiation) {
            json()
        }
        routing {
            when (mode) {
                TcpLookupHandler.MODE_NAME -> {
                    get(targetPath) {
                        processes()
                    }
                }
                SocketmapLookupHandler.MODE_NAME -> {
                    get(targetPath) {
                        processes()
                    }
                }
                PolicyRequestHandler.MODE_NAME -> {
                    post<String>(targetPath) {
                        assertHeaders()
                        processes(it)
                    }
                }
                else -> fail("Unknown mode: $mode")
            }
        }
    }

    server.start()

    runBlocking(Dispatchers.IO) {
        val serverBindPort = server.engine.resolvedConnectors().first().port
        val endpoint = Endpoint(
            name = name,
            target = Url("http://127.0.0.1:$serverBindPort$targetPath"),
            bindAddress = "127.0.0.1",
            bindPort = 0,
            authToken = name,
            mode = mode,
        )

        val configuration = Configuration(
            endpoints = listOf(endpoint),
        )
        val selector = SelectorManager(Dispatchers.IO)
        val session = startSession(configuration)
        val listenSocket = aSocket(selector)
            .tcp()
            .connect(session.bindAddresses().first())

        val writeChannel = ByteChannel()
        listenSocket.attachForWriting(writeChannel)
        val readChannel = ByteChannel()
        listenSocket.attachForReading(readChannel)

        val ctx = TestContext(readChannel, writeChannel, calls, endpoint)
        ctx.block()

        session.close()
    }
}