// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package cc.unitmesh.devti.sketch.run

import com.intellij.util.ObjectUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * This should be used instead of [Process.onExit]`().await()`.
 * @return [Process.exitValue]
 */
suspend fun Process.awaitExit(): Int {
    return loopInterruptible { timeout: Duration ->
        if (timeout.isInfinite()) {
            @Suppress("UsePlatformProcessAwaitExit")
            Attempt.success(waitFor())
        }
        else if (waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)) {
            Attempt.success(exitValue())
        }
        else {
            Attempt.tryAgain()
        }
    }
}

val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(parallelism = Int.MAX_VALUE)

// max wait per attempt: 100 * 10ms = 1 second
private const val ATTEMPT_COUNT: Int = 100
private val stepDuration: Duration = 10.milliseconds

/**
 * Loops interruptibly with linearly incremented [Duration] until a value is produced.
 *
 * Runs [interruptibleAction] on [Dispatchers.IO].
 * Loops again with linearly increasing timeout if [interruptibleAction] returns [Attempt.tryAgain] value.
 * After [several attempts][ATTEMPT_COUNT] with [increasing][stepDuration] timeouts,
 * interruptibly blocks a thread in a special unbounded dispatcher with [Duration.INFINITE] timeout.
 * The last iteration is not allowed to return [Attempt.tryAgain].
 */
internal suspend fun <T> loopInterruptible(interruptibleAction: (Duration) -> Attempt<T>): T {
    for (attempt in 0 until ATTEMPT_COUNT) {
        val result = runInterruptible(Dispatchers.IO) {
            interruptibleAction(stepDuration * attempt)
        }
        if (result.isSuccess()) {
            return result.get()
        }
    }
    @OptIn(DelicateCoroutinesApi::class)
    val result = runInterruptible(blockingDispatcher) {
        interruptibleAction(Duration.INFINITE)
    }
    return result.get()
}

@JvmInline
internal value class Attempt<T> private constructor(private val value: T) {

    fun isSuccess(): Boolean {
        return value !== tryAgain
    }

    fun get(): T {
        check(isSuccess())
        return value
    }

    companion object {

        fun <T> success(value: T): Attempt<T> {
            return Attempt(value)
        }

        fun <T> tryAgain(): Attempt<T> {
            @Suppress("UNCHECKED_CAST")
            return Attempt(tryAgain) as Attempt<T>
        }

        private val tryAgain: Any = ObjectUtils.sentinel("try again")
    }
}
