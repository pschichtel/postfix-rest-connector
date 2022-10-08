package tel.schich.postfixrestconnector.mocks

import tel.schich.postfixrestconnector.Endpoint
import tel.schich.postfixrestconnector.PolicyRequestHandler
import java.net.http.HttpClient.newHttpClient
import java.nio.channels.SocketChannel
import java.util.UUID

class MockPolicyRequestHandler(endpoint: Endpoint) : PolicyRequestHandler(endpoint, newHttpClient(), "test") {
    private var data: List<Pair<String, String>>? = null
    override fun handleRequest(ch: SocketChannel, id: UUID, params: List<Pair<String, String>>) {
        data = params
        super.handleRequest(ch, id, params)
    }

    fun getData(): List<Pair<String, String>>? {
        val copy = data
        data = null
        return copy
    }
}
