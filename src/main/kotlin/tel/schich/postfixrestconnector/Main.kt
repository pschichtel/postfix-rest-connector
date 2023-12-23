package tel.schich.postfixrestconnector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    val configPath = if (args.size == 1) {
        Paths.get(args[0])
    } else {
        println("Usage: <config path>")
        exitProcess(1)
    }

    val configContent = withContext(Dispatchers.IO) {
        Files.readString(configPath)
    }
    val config = Json.decodeFromString<Configuration>(configContent)
    val connector = RestConnector()
    val session = connector.start(config)

    Runtime.getRuntime().addShutdownHook(Thread { session.close() })

    session.join()
}
