/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.androidx.ci.util

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okio.Timeout
import org.apache.logging.log4j.kotlin.logger
import org.apache.logging.log4j.kotlin.loggerOf
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
internal annotation class Retry(val times: Int = 3)

typealias RetryScheduler = (delay: Long, timeUnit: TimeUnit, block: suspend () -> Unit) -> Unit

private class RetryCallAdapter(
    val scheduler: RetryScheduler,
    val delegate: CallAdapter<Any?, Any?>,
    val retry: Int,
) : CallAdapter<Any?, Any?> {
    override fun responseType(): Type {
        return delegate.responseType()
    }

    override fun adapt(call: Call<Any?>): Any {
        return RetryCall(scheduler, delegate.adapt(call) as Call<Any?>, retry)
    }
}

private class RetryCall(
    private val scheduler: RetryScheduler,
    private var delegate: Call<Any?>,
    private val retryLimit: Int
) : Call<Any?> {
    private val cancelled = AtomicBoolean(false)
    private var retryCount = 0
    override fun clone(): Call<Any?> {
        return RetryCall(scheduler, delegate.clone(), retryLimit)
    }

    override fun execute(): Response<Any?> {
        error("sync ops are not supported yet")
    }

    override fun enqueue(callback: Callback<Any?>) {
        if (isCanceled || isExecuted) {
            return
        }
        delegate.enqueue(
            RetryCallback(
                delegate = callback,
                shouldRetry = { response: Response<Any?>? ->
                    retryCount < retryLimit && (
                        response == null || response.code() >= 500
                        )
                },
                retry = {
                    retry(callback)
                }
            )
        )
    }

    private fun retry(callback: Callback<Any?>) {
        retryCount ++
        val delaySeconds = minOf(10, retryCount * 2).toLong() // 1 4 9 10 10 10 ...
        delegate = delegate.clone()
        scheduler(
            delaySeconds,
            TimeUnit.SECONDS
        ) {
            enqueue(callback)
        }
    }

    override fun isExecuted(): Boolean {
        return delegate.isExecuted
    }

    override fun cancel() {
        cancelled.set(true)
        delegate.cancel()
    }

    override fun isCanceled(): Boolean {
        return cancelled.get() || delegate.isCanceled
    }

    override fun request(): Request {
        return delegate.request()
    }

    override fun timeout(): Timeout {
        return delegate.timeout()
    }
}

private class RetryCallback(
    val delegate: Callback<Any?>,
    val shouldRetry: (Response<Any?>?) -> Boolean,
    val retry: () -> Unit
) : Callback<Any?> {
    override fun onResponse(call: Call<Any?>, response: Response<Any?>) {
        if (shouldRetry(response)) {
            retry()
        } else {
            delegate.onResponse(call, response)
        }
    }

    override fun onFailure(call: Call<Any?>, t: Throwable) {
        if (shouldRetry(null)) {
            retry()
        } else {
            delegate.onFailure(call, t)
        }
    }
}

internal class RetryCallAdapterFactory(
    private val scheduler: RetryScheduler
) : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *> {
        val annotation = annotations.firstOrNull() {
            it.annotationClass == Retry::class
        } as? Retry
        val delegate = retrofit.nextCallAdapter(
            this,
            returnType,
            annotations
        )
        if (annotation == null) {
            return delegate
        } else {
            return RetryCallAdapter(
                scheduler = scheduler,
                delegate = delegate as CallAdapter<Any?, Any?>,
                retry = annotation.times
            )
        }
    }

    companion object {
        /**
         * Retry call adapter factory using global scope.
         *
         * This is not great normally but is OK for our runner since it does 1 thing and then exists.
         */
        val GLOBAL = RetryCallAdapterFactory { time, unit, callback ->
            GlobalScope.launch {
                delay(unit.toMillis(time))
                callback()
            }
        }
    }
}

/**
 * Adds BODY logging for http requests.
 */
internal fun OkHttpClient.Builder.withLog4J(
    level: HttpLoggingInterceptor.Level,
    klass: KClass<*>
): OkHttpClient.Builder {
    return if (level == HttpLoggingInterceptor.Level.NONE) {
        this
    } else {
        val log4jLogger = loggerOf(klass.java)
        val loggingInterceptor = HttpLoggingInterceptor(
            logger = {
                log4jLogger.info(it)
            }
        )
        loggingInterceptor.level = level
        this.addInterceptor(loggingInterceptor)
    }
}
