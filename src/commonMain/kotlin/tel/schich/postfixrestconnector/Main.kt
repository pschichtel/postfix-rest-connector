package tel.schich.postfixrestconnector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource

expect fun errorExit(): Nothing

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) = runBlocking(Dispatchers.IO) {
    val configPath = if (args.size == 1) {
        Path(args[0])
    } else {
        println("Usage: <config path>")
        errorExit()
    }

    val fileSource = try {
        SystemFileSystem.source(configPath).buffered()
    } catch (e: FileNotFoundException) {
        println("No file found at $configPath!")
        errorExit()
    } catch (e: IOException) {
        println("Failed to read file at $configPath: ${e.message}")
        errorExit()
    }
    val config = Json.decodeFromSource<Configuration>(fileSource)
    val session = startSession(config)

    session.join()
}
