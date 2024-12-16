@file:Suppress("HasPlatformType")

package org.jetbrains.research.guava

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.*
import org.junit.*
import com.google.common.cache.*
import java.util.concurrent.*

class ConcurrentCombinedMapTest {
    private val cache: Cache<Int, String> = CacheBuilder.newBuilder()
        .softValues()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()
    private val map: ConcurrentMap<Int, String> = cache.asMap()

    @Operation
    fun put(key: Int, value: String) = map.put(key, value)

    @Operation
    fun get(key: Int) = map[key]

    @Operation
    fun remove(key: Int) = map.remove(key)

    @Operation
    fun containsKey(key: Int) = map.containsKey(key)

    @Operation
    fun containsValue(value: String) = map.containsValue(value)

//    @Operation
//    fun isEmpty() = map.isEmpty()

    @Operation
    fun size() = map.size

    @Test
    fun concurrntCombinedMapStressTest() = StressOptions().check(this::class)

    @Test
    fun concurrntCombinedMapModelCheckingTest() = ModelCheckingOptions().check(this::class)
}
