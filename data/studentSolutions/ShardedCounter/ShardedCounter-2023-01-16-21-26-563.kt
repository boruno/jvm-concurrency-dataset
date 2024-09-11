package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val locked = atomic(false);

    enum class Op {
        INC, GET
    }

    private fun lock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        locked.value = false
    }


    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        var index = Random().nextInt(ARRAY_SIZE)
        while (!counters[index].compareAndSet(expect = counters[index].value, update = counters[index].value + 1)) {
            index = Random().nextInt(ARRAY_SIZE)
            return
        }

    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var result = 0
        for (i in 0 until ARRAY_SIZE) {
            result += counters[i].value
        }
        return result
    }


}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME
