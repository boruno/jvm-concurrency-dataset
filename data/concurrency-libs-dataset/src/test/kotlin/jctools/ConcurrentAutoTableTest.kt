@file:Suppress("HasPlatformType")

package jctools

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.jctools.maps.*

class ConcurrentAutoTableTest {
    private val table = ConcurrentAutoTable()

    @Operation
    fun increment() = table.increment()

    @Operation
    fun decrement() = table.decrement()

    @Operation
    fun add(value: Long) = table.add(value)

    @Operation
    fun sum(): Long {
        val sumMethod = table.javaClass.getMethod("sum")
        return sumMethod.invoke(table) as Long
    }

    @Operation
    fun get() = table.get()

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
