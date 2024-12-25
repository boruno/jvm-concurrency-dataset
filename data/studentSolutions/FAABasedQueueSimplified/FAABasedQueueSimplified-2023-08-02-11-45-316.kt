//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while(true) {
            if (!shouldDeq()) return null
            val index = deqIdx.getAndIncrement()
            if(infiniteArray[index].compareAndSet(null, POISONED)) {
                continue
            }
            return infiniteArray[index].value as E
        }
    }

    private fun shouldDeq(): Boolean {
        while (true) {
            val encIndex = enqIdx.value
            val deqIndex = deqIdx.value
            if (encIndex != enqIdx.value) continue
            return deqIndex >= encIndex
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
