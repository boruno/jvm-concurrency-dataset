//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val idx = enqIdx.getAndIncrement()
        while (true){

            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if ( infiniteArray[idx].compareAndSet(null, element))
                return
        }

    }

    private fun tryToDeque(): Boolean {
        while (true) {
            val eqIdxTmp = enqIdx.value
            val deqIdxTmp = deqIdx.value
            if (eqIdxTmp != enqIdx.value)
                continue
            return deqIdxTmp >= eqIdxTmp
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!tryToDeque())
                return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val idx = deqIdx.getAndIncrement()

            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            // todo while loop

            if (infiniteArray[idx].compareAndSet(null, POISONED))
                continue
            return infiniteArray[idx].value as E
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
