package com.beust.kobalt.misc

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public interface KobaltLogger {
    val logger : Logger
        get() = LoggerFactory.getLogger(javaClass.getSimpleName())

//    private fun log(method: Function1<String, Void>, message: String) =
//        method.invoke(message)

    companion object {

        public var LOG_LEVEL : Int = 1

        fun log(level: Int, s: String) {
            if (level <= LOG_LEVEL) {
                LoggerFactory.getLogger(KobaltLogger::class.java.getSimpleName()).info(s)
            }
        }

        fun warn(s: String, e: Throwable? = null) {
            LoggerFactory.getLogger(KobaltLogger::class.java.getSimpleName()).warn(s, e)
        }
    }

    final fun log(level: Int = 1, message: String) {
        // Compiler crashing if I use LOG_LEVEL here
        // Caused by: java.lang.VerifyError: Bad invokespecial instruction: current class isn't
        // assignable to reference class.
        if (level <= LOG_LEVEL) {
            logger.info(message)
        }
    }

    final fun debug(message: String) {
        logger.debug(message)
    }

    final fun error(message: String, e: Throwable? = null) {
        logger.error("***** ${message}", e)
    }

    final fun warn(message: String, e: Throwable? = null) {
        logger.warn(message, e)
    }
}