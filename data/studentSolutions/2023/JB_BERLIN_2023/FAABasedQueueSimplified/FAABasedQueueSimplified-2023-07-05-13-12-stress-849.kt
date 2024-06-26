package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (isEmpty()) return null
            val i = deqIdx.getAndIncrement()
            val value = infiniteArray[i].value
            if (!infiniteArray[i].compareAndSet(null, POISONED)) {
                return value as E
            }
        }
    }

    // нам нельзя возвращать ложно-позитивные
    // значит пусть enq может быть больше, чем на самом деле
    private fun isEmpty(): Boolean {
        while (true) {
            val deq = deqIdx.value
            val enq = enqIdx.value
            if (deq != deqIdx.value) continue
            return deq >= enq
        }
    }
}

private val POISONED = Any()
