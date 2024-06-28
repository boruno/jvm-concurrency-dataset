package day2

import day1.Queue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.getAndUpdate

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
//        val i = enqIdx.value
//        enqIdx.value = i + 1
            val i = enqIdx.incrementAndGet()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray[i].compareAndSet(null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldDequeue()) continue
            val i = deqIdx.incrementAndGet()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            val element = infiniteArray[i].getAndUpdate { v ->
                when (v) {
                    null -> POISONED
                    else -> v
                }
            }
            if (element != null) {
                return element as E
            }
        }
    }

    private fun shouldDequeue(): Boolean {
        while (true) {
            val curEnqIndex = enqIdx.value
            val curDeqIndex = deqIdx.value
            if (curEnqIndex != enqIdx.value) continue
            return curDeqIndex >= curEnqIndex
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
