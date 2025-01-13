//package day2

import Queue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.max
import kotlin.math.min

class FAABasedQueueSimplified<E> : Queue<E> {
    private val infiniteArray = AtomicReferenceArray<Any?>(1024) // conceptually infinite array
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        // TODO: Increment the counter atomically via Fetch-and-Add.
        // TODO: Use `getAndIncrement()` function for that.
        while (true) {
            val i = enqIdx.getAndIncrement()
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (infiniteArray.compareAndSet(i.toInt(), null, element)) {
                println("Enqueued $element ${Thread.currentThread().id} encIdx: $i")
                return
            }
        }

    }

    private fun isQueueEmpty(): Boolean {
        while (true) {
            val curEnq = enqIdx.get()
            val curDeq = deqIdx.get()
            if (curEnq != enqIdx.get())
                continue
            return curDeq < curEnq
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        // Is this queue empty?
        while (true) {
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val curDeq = deqIdx.getAndIncrement()
            if (!isQueueEmpty()) {
                return null
            }

            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (infiniteArray.compareAndSet(curDeq.toInt(), null, POISONED)) {
                continue
            }
            return infiniteArray.get(curDeq.toInt()) as E
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
}

// TODO: poison cells with this value.
private val POISONED = Any()
