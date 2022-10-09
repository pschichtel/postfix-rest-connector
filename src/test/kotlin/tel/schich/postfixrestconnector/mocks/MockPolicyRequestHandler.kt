package tel.schich.postfixrestconnector.mocks

import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.utils.io.ByteWriteChannel
import tel.schich.postfixrestconnector.Endpoint
import tel.schich.postfixrestconnector.PolicyRequestHandler
import tel.schich.postfixrestconnector.TestHttpClient
import java.util.UUID

class MockPolicyRequestHandler(endpoint: Endpoint) : PolicyRequestHandler(endpoint, TestHttpClient) {
    private var data: Parameters? = null
    override suspend fun handleRequest(ch: ByteWriteChannel, id: UUID, params: Parameters) {
        data = params
    }

    fun getData(): Parameters? {
        val data = this.data ?: return null

        return with(ParametersBuilder()) {
            appendAll(data)
            build()
        }
    }

    fun getDataAsPairs() = getData()?.entries()?.flatMap { entry -> entry.value.map { Pair(entry.key, it) } }
}
