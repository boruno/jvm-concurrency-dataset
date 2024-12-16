@file:Suppress("HasPlatformType")

package org.jetbrains.research.juc

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import java.util.concurrent.*

class ConcurrentHashMapTest {
    private val map = ConcurrentHashMap<Int, String>()

    @Operation
    fun put(key: Int, value: String) = map.put(key, value)

    @Operation
    fun get(key: Int) = map.get(key)

    @Operation
    fun remove(key: Int) = map.remove(key)

    @Operation
    fun containsKey(key: Int) = map.containsKey(key)

    @Operation
    fun containsValue(value: String) = map.containsValue(value)

    @Test
    fun concurrentHashMapStressTest() = StressOptions().check(this::class)

    @Test
    fun concurrentHashMapModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
