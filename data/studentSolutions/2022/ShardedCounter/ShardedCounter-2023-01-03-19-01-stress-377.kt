package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val got = atomic(false)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        counters[ThreadLocalRandom.current().nextInt()].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        while (!got.compareAndSet(expect = false, update = true)) {
        }
        var ans = 0
        for (i in 0 until counters.size) {
            ans += counters[i].value
        }
        got.value = false
        return ans
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME