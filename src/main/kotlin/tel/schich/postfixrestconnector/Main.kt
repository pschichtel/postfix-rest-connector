package tel.schich.postfixrestconnector

import java.nio.channels.spi.SelectorProvider
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = if (args.size == 1) {
        Paths.get(args[0])
    } else {
        System.err.println("Usage: <config path>")
        exitProcess(1)
    }

    RestConnector().use { restConnector ->
        Runtime.getRuntime().addShutdownHook(Thread { restConnector.stop() })
        restConnector.start(SelectorProvider.provider(), config)
    }
}
