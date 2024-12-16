@file:Suppress("HasPlatformType")

package org.jetbrains.research.jctools

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import org.jctools.maps.*

class NonBlockingHashMapLongTest {
    private val map = NonBlockingHashMapLong<String>()

    @Operation
    fun put(key: Long, value: String) = map.put(key, value)

    @Operation
    fun get(key: Long) = map.get(key)

    @Operation
    fun remove(key: Long) = map.remove(key)

    @Operation
    fun containsKey(key: Long) = map.containsKey(key)

    @Operation
    fun containsValue(value: String) = map.containsValue(value)

//    @Operation
//    fun isEmpty() = map.isEmpty()
//
//    @Operation
//    fun size() = map.size

    @Test
    fun nonBlockingHashMapLongStressTest() = StressOptions().check(this::class)

    @Test
    fun nonBlockingHashMapLongModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
