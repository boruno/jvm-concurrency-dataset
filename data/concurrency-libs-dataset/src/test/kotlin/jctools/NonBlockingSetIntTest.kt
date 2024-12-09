@file:Suppress("HasPlatformType")

package jctools

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.jctools.maps.*

class NonBlockingSetIntTest {
    private val set = NonBlockingSetInt()

    @Operation
    fun add(e: Int) = set.add(e)

    @Operation
    fun remove(e: Int) = set.remove(e)

    @Operation
    fun contains(e: Int) = set.contains(e)

//    @Operation
//    fun isEmpty() = set.isEmpty()

//    @Operation
//    fun size() = set.size

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions().check(this::class)
}
