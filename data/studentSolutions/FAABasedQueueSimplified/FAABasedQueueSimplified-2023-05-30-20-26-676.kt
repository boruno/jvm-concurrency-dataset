//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            val i = enqIdx.getAndIncrement()
            if (infiniteArray[i].compareAndSet(null, element)) return
        }
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
//        infiniteArray[i].value = element
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        if (deqIdx.value > enqIdx.value) return null
//        while (true) {
//            val e1 = enqIdx.value
//            val d1 = deqIdx.value
//            val e2 = enqIdx.value
//            if (e1 != e2) continue
//            if (d1 >= e2) return null
//            else break
//        }

        while (true) {
            val i = deqIdx.getAndIncrement()
            val value = infiniteArray[i].value
            if (infiniteArray[i].compareAndSet(null, POISONED)) continue
            return value as? E
        }
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
