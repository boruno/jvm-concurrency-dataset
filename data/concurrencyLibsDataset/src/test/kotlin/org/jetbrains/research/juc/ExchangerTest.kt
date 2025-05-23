@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ExchangerTest {
    private val exchanger = Exchanger<Int>()

    @Operation
    fun exchange(value: Int) = exchanger.exchange(value)

    @Operation
    fun exchangeWithTimeout(value: Int, timeout: Long, unit: TimeUnit) = exchanger.exchange(value, timeout, unit)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun exchangerStressTest() = StressOptions().check(this::class)

    @Ignore("Blocking structures are currently unsupported")
    @Test
    fun exchangerModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
