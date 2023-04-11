package dev.androidx.ci.config

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.logging.log4j.kotlin.loggerOf
import kotlin.reflect.KClass

/**
 * Configuration for OkHttp & Retrofit.
 */
interface HttpConfigAdapter {
    fun createCallFactory(
        delegate: okhttp3.Call.Factory
    ): okhttp3.Call.Factory = delegate

    fun interceptors(): List<Interceptor> = emptyList()

    fun interface Factory {
        /**
         * Creates an [HttpConfigAdapter] for the given [klass].
         *
         * @param klass The class which is creating the Retrofit / OkHttp clients.
         * @return A HttpConfigAdapter that will be used to configure the network stack.
         */
        fun create(klass: KClass<*>): HttpConfigAdapter

        class Logging(
            private val logLevel: HttpLoggingInterceptor.Level
        ) : Factory {
            override fun create(klass: KClass<*>): HttpConfigAdapter {
                return LoggingConfig(
                    logClass = klass,
                    logLevel = logLevel
                )
            }
        }

        object NoOp : Factory {
            override fun create(klass: KClass<*>): HttpConfigAdapter {
                return object : HttpConfigAdapter {}
            }
        }
    }

    class LoggingConfig(
        private val logClass: KClass<*>,
        private val logLevel: HttpLoggingInterceptor.Level
    ) : HttpConfigAdapter {
        override fun interceptors(): List<Interceptor> {
            val log4jLogger = loggerOf(logClass.java)
            val loggingInterceptor = HttpLoggingInterceptor(
                logger = {
                    log4jLogger.info(it)
                }
            )
            loggingInterceptor.level = logLevel
            return listOf(
                loggingInterceptor
            )
        }
    }
}