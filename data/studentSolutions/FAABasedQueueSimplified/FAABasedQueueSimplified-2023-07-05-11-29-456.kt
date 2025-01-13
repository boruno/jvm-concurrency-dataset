//package day2

import kotlinx.atomicfu.*

@Suppress("KotlinConstantConditions")
class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(15) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        val i = enqIdx.getAndIncrement()
        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.
        infiniteArray[i].compareAndSet(null, element)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (shouldNotTryDeque()) return null
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.getAndIncrement()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            val element = infiniteArray[i].value
            if (element == null) {
                infiniteArray[i].compareAndSet(null, POISONED)
            }
        }
    }

    fun shouldNotTryDeque(): Boolean {
        while (true) {
            val curDeqInx = deqIdx.value
            val curEnqInx = enqIdx.value
            if (curDeqInx != deqIdx.value) continue
            return curDeqInx >= curEnqInx
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
