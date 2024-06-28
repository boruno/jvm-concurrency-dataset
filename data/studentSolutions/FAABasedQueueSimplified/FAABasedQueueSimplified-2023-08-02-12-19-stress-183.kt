package day2

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
//            if (infiniteArray[i].compareAndSet(POISONED, e))
            if (infiniteArray[i].compareAndSet(null, element))
                return

//            if (infiniteArray[i])
//            if (infiniteArray[i])
        }
    }

    fun shouldNotTryDequeue(): Boolean {
        while (true) {
            val enq = enqIdx.value
            val deq = deqIdx.value
            if (enq != enqIdx.value) continue
            return deq > enq
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        while (true) {
            if (shouldNotTryDequeue()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.

            val i = deqIdx.getAndIncrement()
//        deqIdx.value = i + 1
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.

            if (infiniteArray[i].compareAndSet(null, POISONED)) {
                continue
            }
            return infiniteArray[i].value as E?
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
