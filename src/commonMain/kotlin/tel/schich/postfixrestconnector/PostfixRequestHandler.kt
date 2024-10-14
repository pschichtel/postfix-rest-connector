package tel.schich.postfixrestconnector

interface PostfixRequestHandler {
    val endpoint: Endpoint
    fun createState(): ConnectionState
}
