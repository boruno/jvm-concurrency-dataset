package day2

import day1.*
import kotlinx.atomicfu.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = atomicArrayOfNulls<Any?>(1024) // conceptually infinite array
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    override fun enqueue(element: E) {
        while(true){
            val i = enqIdx.getAndIncrement()

            if(infiniteArray[i].value!= POISONED &&
                infiniteArray[i].compareAndSet(null,element))
                return
        }
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.

        // TODO: Atomically install the element into the cell
        // TODO: if the cell is not poisoned.

    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while(true)
        {
            //if (deqIdx.value>=enqIdx.value) return null
            val e1 = enqIdx.value;
            val d1 = deqIdx.value;
            val e2 = enqIdx.value;
            if(enqIdx.value<=deqIdx.value) return null
            val i = deqIdx.getAndIncrement()
            if(infiniteArray[i].compareAndSet(null,POISONED)) continue
            return infiniteArray[i].value as E
        }
        // Is this queue empty?

        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.

        // TODO: Try to retrieve an element if the cell contains an
        // TODO: element, poisoning the cell if it is empty.
        //return infiniteArray[i].value as E
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
