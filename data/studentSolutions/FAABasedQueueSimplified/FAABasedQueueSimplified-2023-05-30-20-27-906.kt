//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) return
        }

        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.

//        enqIdx.value = i + 1
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
//        infiniteArray[i].value = element
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?

        var eI1: Int
        var dI: Int
        while (true) {
            eI1 = enqIdx.value
            dI = deqIdx.value

            if (eI1 == enqIdx.value) break
        }

        if (eI1 <= dI) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = deqIdx.getAndIncrement()
//        deqIdx.value = i + 1

        if (infiniteArray[i].compareAndSet(null, POISONED)) {
            return null
        }

        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        return infiniteArray[i].value as E
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
