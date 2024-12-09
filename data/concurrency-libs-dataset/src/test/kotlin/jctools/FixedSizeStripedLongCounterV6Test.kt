@file:Suppress("HasPlatformType")

package jctools

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.lang.reflect.Constructor

class FixedSizeStripedLongCounterV6Test {
    private val counter: Any

    init {
        val clazz = Class.forName("org.jctools.counters.FixedSizeStripedLongCounterV6")
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
    fun stressTest() = StressOptions().check(this::class)

    @Ignore("Temporary ignored due to being an internal class of JCTools")
    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}