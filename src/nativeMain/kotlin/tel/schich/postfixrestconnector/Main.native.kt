package tel.schich.postfixrestconnector

import kotlin.system.exitProcess

actual fun errorExit(): Nothing {
    exitProcess(1)
}