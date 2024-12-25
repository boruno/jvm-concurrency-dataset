//package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)


    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
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
           if (enqIdx.value == deqIdx.value) return null
           val i = deqIdx.incrementAndGet()
           val value = infiniteArray[i].compareAndSet(infiniteArray[i].value, POISONED)
           if (value != null && value != POISONED){
               return value as E
           }
       }
   }
}

// TODO: poison cells with this value.
private val POISONED = Any()
