//package mpp.counter

import kotlinx.atomicfu.AtomicBooleanArray
import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val locks = Lock()
    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        while (true) {
            val random = Random.nextInt() % ARRAY_SIZE
            if (locks.tryLock(random)) {
                counters[random].getAndIncrement()
                locks.unLock(random)
            }
        }
        // TODO("implement me!")
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        for (i in 0 until ARRAY_SIZE) {
            while (true) {
                if (locks.tryLock(i)) {
                    break
                }
            }
        }
        var result = 0
        for (i in 0 until ARRAY_SIZE) {
            result += counters[i].value
            locks.unLock(i)
        }
        println(result)
        return result
        // TODO("implement me!")
    }

    private class Lock {
        private val locked = AtomicBooleanArray(ARRAY_SIZE)

        fun tryLock(i: Int) = locked[i].compareAndSet(false, true)
        fun unLock(i: Int) {
            locked[i].getAndSet(false)
        }
        fun isLocked(i: Int) = locked[i].value
    }
}


private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME