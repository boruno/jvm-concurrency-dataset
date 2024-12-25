//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.log2
import kotlin.math.roundToInt

interface DynamicArray<E> {
    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun get(index: Int): E

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the [size] of this array.
     */
    fun put(index: Int, element: E)

    /**
     * Adds the specified [element] to this array
     * increasing its [size].
     */
    fun pushBack(element: E)

    /**
     * Returns the current size of this array,
     * it increases with [pushBack] invocations.
     */
    val size: Int
}

class DynamicArrayImpl<E>() : DynamicArray<E> {
    private val memory = atomicArrayOfNulls<Array<AtomicReference<E>>>(32)
    private val descriptor = atomic(Descriptor<E>(0, null))

    init {
//        memory[0]
    }
    override fun get(index: Int): E = at(index).get()

    override fun put(index: Int, element: E) {
        at(index).set(element)
    }

    override fun pushBack(element: E) {
        while (true) {
            val current = descriptor.value
            if (current.writeOp != null) {
                completeWrite(current.writeOp)
            }
            val bucket = if (current.size == 0) {
                0
            } else {
                1 + log2(current.size.takeHighestOneBit().toDouble()).roundToInt()
            }
            if (memory[bucket].value == null) {
                allocBucket(bucket)
            }
            val writeOp = WriteDesc<E>(at(current.size).get(), element, current.size)
            val next = Descriptor(current.size + 1, writeOp)
            if (descriptor.compareAndSet(current, next)) {
                if (next.writeOp != null) {
                    completeWrite(next.writeOp)
                }
                break
            }
        }
    }

    override val size: Int
        get() {
            val descriptor = descriptor.value
            val size = descriptor.size
            val writeOp = descriptor.writeOp
            if (writeOp != null && writeOp.pending) {
                return size - 1
            }
            return size
        }

    private fun at(i: Int): AtomicReference<E> {
        val idx = i - i.takeHighestOneBit()
        val bucket: Int = if (i == 0) {
            0
        } else {
            1 + log2(i.takeHighestOneBit().toDouble()).roundToInt()
        }
        return memory[bucket].value!![idx]
    }

    private fun completeWrite(writeOp: WriteDesc<E>) {
        if (writeOp.pending) {
            at(writeOp.pos).compareAndSet(writeOp.oldValue, writeOp.newValue)
            writeOp.pending = false
        }
    }

    private fun allocBucket(bucket: Int) {
        val bucketSize = 1 shl (bucket - 1)
        val mem = Array(bucketSize) { AtomicReference<E>(null) }
        memory[bucket].compareAndSet(null, mem)
    }
}

private class Descriptor<E>(
    val size: Int,
    val writeOp: WriteDesc<E>?
)

private class WriteDesc<E>(
    val oldValue: E,
    val newValue: E,
    val pos: Int
) {
    var pending = false
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME