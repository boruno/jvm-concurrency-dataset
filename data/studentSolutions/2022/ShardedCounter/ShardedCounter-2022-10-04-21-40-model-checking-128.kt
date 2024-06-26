package mpp.counter

import kotlinx.atomicfu.AtomicInt
import java.util.concurrent.atomic.AtomicInteger

class ShardedCounter {
    private val c = AtomicInteger()

    fun inc(): Int {
        return c.getAndIncrement()
        TODO("implement me")
    }

    fun get(): Int {
        return c.get()
        TODO("implement me")
    }
}