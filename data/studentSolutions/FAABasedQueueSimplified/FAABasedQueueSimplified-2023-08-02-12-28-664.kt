//package day2

import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {

        while (true) {
            if (infiniteArray[enqIdx.getAndIncrement()].compareAndSet(null, element)) {
                return
            }
        }

        /**
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = enqIdx.value
        enqIdx.value = i + 1
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
        infiniteArray[i].value = element
        **/
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (enqIdx.value <= deqIdx.value) return null

            val i = deqIdx.getAndIncrement()

            if (infiniteArray[i].compareAndSet(null, POISONED)) {
                continue
            }

            return infiniteArray[i].value as E
        }


        /**
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = deqIdx.value
        deqIdx.value = i + 1
        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        return infiniteArray[i].value as E
         **/
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val currentEnqIndex = enqIdx.value
            val currentDeqIndex = deqIdx.value

            if (currentEnqIndex != currentDeqIndex) {
                continue
            }

            return true
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
