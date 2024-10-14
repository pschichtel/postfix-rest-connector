package tel.schich.postfixrestconnector

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource

expect fun errorExit(): Nothing

@OptIn(ExperimentalSerializationApi::class)
suspend fun main(args: Array<String>) {
    val configPath = if (args.size == 1) {
        Path(args[0])
    } else {
        println("Usage: <config path>")
        errorExit()
    }

    val fileSource = SystemFileSystem.source(configPath).buffered()
    val config = Json.decodeFromSource<Configuration>(fileSource)
    val session = startSession(config)

    session.join()
}
