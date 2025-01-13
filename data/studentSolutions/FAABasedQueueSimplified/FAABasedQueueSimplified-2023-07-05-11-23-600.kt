//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(35) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
//        val i = enqIdx.value
//        enqIdx.value = i + 1
        while (true) {
            val i = enqIdx.getAndAdd(1)
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            infiniteArray[i].compareAndSet(null,  element)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!shouldDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.

            val i = deqIdx.getAndAdd(1)
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            var count = 0
            while (count < 10) {
                val value = infiniteArray[i].value
                if (value != null) return value as E
                count++
            }
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            else return infiniteArray[i].value as E
        }
    }

    private fun shouldDequeue(): Boolean {
        while (true) {
            val deq = deqIdx.value
            val enq = enqIdx.value
            if (deq != deqIdx.value) continue
            return deq < enq
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
