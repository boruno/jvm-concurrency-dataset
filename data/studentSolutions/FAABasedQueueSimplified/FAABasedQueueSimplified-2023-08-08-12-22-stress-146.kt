package day2

import day1.*
import java.util.concurrent.atomic.*
import kotlin.math.*

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        // Increment the counter atomically via Fetch-and-Add.
        // Use `getAndIncrement()` function for that.
        while (true) {
            val i = enqIdx.getAndIncrement()
            // Atomically install the element into the cell
            // if the cell is not poisoned.
            // else restart enqueue
            if (infiniteArray.compareAndSet(i.toInt(), null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty? - only >= works correctly here
//            if (deqIdx.get() >= enqIdx.get()) return null
            if (!shouldTryToDequeue()) return null
            // Increment the counter atomically via Fetch-and-Add.
            // Use `getAndIncrement()` function for that.
            val curDeqIdx = deqIdx.getAndIncrement().toInt()
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (infiniteArray.compareAndSet(curDeqIdx, null, POISONED)) {
                continue
            }
            return infiniteArray.get(curDeqIdx) as E?
//            when (val element = infiniteArray.get(curDeqIdx)) {
//                POISONED -> continue
//                else -> return element as E?
//            }
        }
    }

    override fun validate() {
        for (i in 0 until min(deqIdx.get().toInt(), enqIdx.get().toInt())) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` at the end of the execution"
            }
        }
        for (i in max(deqIdx.get().toInt(), enqIdx.get().toInt()) until infiniteArray.length()) {
            check(infiniteArray[i] == null || infiniteArray[i] == POISONED) {
                "`infiniteArray[$i]` must be `null` or `POISONED` with `enqIdx = ${enqIdx.get()}` at the end of the execution"
            }
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while(true) {
            val curD = deqIdx.get()
            val curE = enqIdx.get()
            if (curD != deqIdx.get()) continue
            return curD < curE
        }
    }
}

// TODO: poison cells with this value.
private val POISONED = Any()
