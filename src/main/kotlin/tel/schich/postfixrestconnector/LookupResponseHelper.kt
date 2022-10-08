package tel.schich.postfixrestconnector

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object LookupResponseHelper {
    @JvmStatic
    fun parseResponse(mapper: Json, response: String): List<String> {
        return mapper.decodeFromString(response)
    }

    @JvmStatic
    fun encodeResponse(values: Iterable<String>, separator: String): String {
        val it = values.iterator()
        if (!it.hasNext()) {
            return ""
        }
        val out = StringBuilder(it.next())
        while (it.hasNext()) {
            out.append(separator).append(it.next())
        }
        return out.toString()
    }
}
