@file:Suppress("HasPlatformType")

package org.jetbrains.research.jctools

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.lang.reflect.Constructor

class FixedSizeStripedLongCounterV8Test {
    private val counter: Any

    init {
        val clazz = Class.forName("org.jctools.counters.FixedSizeStripedLongCounterV8")
        val constructor: Constructor<*> = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        counter = constructor.newInstance(10)
    }

    @Operation
    fun increment() = counter.javaClass.getMethod("increment").invoke(counter)

    @Operation
    fun decrement() = counter.javaClass.getMethod("decrement").invoke(counter)

    @Operation
    fun add(value: Long) = counter.javaClass.getMethod("add", Long::class.java).invoke(counter, value)

    @Operation
    fun sum() = counter.javaClass.getMethod("sum").invoke(counter)

    @Ignore("Temporary ignored due to being an internal class of JCTools")
    @Test
    fun fixedSizeStripedLongCounterV8StressTest() = StressOptions().check(this::class)

    @Ignore("Temporary ignored due to being an internal class of JCTools")
    @Test
    fun fixedSizeStripedLongCounterV8ModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
