package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        //val i = enqIdx.value
        //enqIdx.value = i + 1
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
        //infiniteArray[i].value = element

        while (true) {
            val idx = enqIdx.getAndIncrement()
            if (infiniteArray[idx].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
        // Is this queue empty?
        if (enqIdx.value <= deqIdx.value) return null
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        //val i = deqIdx.value
        //deqIdx.value = i + 1
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        //return infiniteArray[i].value as E


            val idx = deqIdx.getAndIncrement()
            if (infiniteArray[idx].compareAndSet(null, POISONED)) continue
            val value = infiniteArray[idx].value
            if (value == POISONED) continue
            return value as E

            //node.compareAndSet(null, POISONED)
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
