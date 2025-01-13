//package day2

import Queue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.max
import kotlin.math.min

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val newIdx = enqIdx.incrementAndGet().toInt()
            if (infiniteArray.compareAndSet(newIdx, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (queueIsEmpty()) {
                return null
            }

            val newDeqIdx = deqIdx.incrementAndGet().toInt()
            val currentValue = infiniteArray.get(newDeqIdx)
            if (infiniteArray.compareAndSet(newDeqIdx, currentValue, null)) {
                return currentValue as E
            }
            infiniteArray.compareAndSet(newDeqIdx, null, POISONED)
        }
    }

    private fun queueIsEmpty(): Boolean {
        while (true) {
            val currentEnqIdx = enqIdx.get()
            val currentDeqIdx = deqIdx.get()
            if (currentEnqIdx == enqIdx.get()) {
                return currentEnqIdx <= currentDeqIdx
            }
        }
    }

    override fun validate() {
        for (i in 0 until min(deqIdx.get().toInt(), enqIdx.get().toInt())) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` at the end of the execution"
            }
        }
        for (i in max(deqIdx.get().toInt(), enqIdx.get().toInt()) until infiniteArray.length()) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `enqIdx = ${enqIdx.get()}` at the end of the execution"
            }
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
