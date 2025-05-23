//package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReferenceArray

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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(capacity = INITIAL_CAPACITY))

    override fun get(index: Int): E {
        val descriptor = core.value
        descriptor.completeOperation()

        if (index >= descriptor.size) {
            throw IllegalArgumentException()
        }

        return descriptor.get(index)
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val oldDescriptor = core.value
            oldDescriptor.completeOperation()

            if (index >= oldDescriptor.size) {
                throw IllegalArgumentException()
            }

            val newDescriptor = Core.copy(oldDescriptor.move(), operation = AtomicMarkableReference({
                oldDescriptor.memory.set(index, element)
            }, false))
            if (core.compareAndSet(oldDescriptor, newDescriptor)) {
                newDescriptor.completeOperation()
                break
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val oldDescriptor = core.value
            oldDescriptor.completeOperation()

            val newDescriptor = Core.createForPush(oldDescriptor.move(), element)
            if (core.compareAndSet(oldDescriptor, newDescriptor)) {
                newDescriptor.completeOperation()
                break
            }
        }
    }

    override val size: Int
        get() {
            val descriptor = core.value
            val size = descriptor.size

            return if (descriptor.operation.isMarked) size - 1 else size
        }
}

private data class Core<E>(
    val size: Int = 0,
    val capacity: Int,
    val leftToMove: Int = 0,
    val operation: AtomicMarkableReference<(() -> Unit)?> = AtomicMarkableReference(null, false),
    val memory: AtomicReferenceArray<E?> = AtomicReferenceArray(capacity),
    val oldMemory: AtomicReferenceArray<E?> = AtomicReferenceArray(0),
) {
    private val moved: Int
        get() = capacity / 2 - leftToMove

    fun get(index: Int): E {
        return memory.get(index) ?: oldMemory.get(index)!!
    }

    fun completeOperation() {
        val op = operation.reference ?: return

        try {
            op.invoke()
        } finally {
            operation.set(null, false)
        }
    }

    fun move(): Core<E> {
        if (leftToMove == 0) {
            return this
        }

        memory.compareAndSet(moved, null, oldMemory.get(moved))
        return copy(this, leftToMove = leftToMove - 1)
    }

    companion object {
        fun <E> copy(
            other: Core<E>,
            size: Int = other.size,
            capacity: Int = other.capacity,
            leftToMove: Int = other.leftToMove,
            operation: AtomicMarkableReference<(() -> Unit)?> = other.operation,
            memory: AtomicReferenceArray<E?> = other.memory,
            oldMemory: AtomicReferenceArray<E?> = other.oldMemory,
        ): Core<E> {
            return Core(size, capacity, leftToMove, operation, memory, oldMemory)
        }

        fun <E> createForPush(oldDescriptor: Core<E>, element: E): Core<E> {
            val memory = if (oldDescriptor.capacity == oldDescriptor.size) {
                AtomicReferenceArray(oldDescriptor.capacity * 2)
            } else {
                oldDescriptor.memory
            }
            val operation: AtomicMarkableReference<(() -> Unit)?> = AtomicMarkableReference({
                memory.compareAndSet(oldDescriptor.size, null, element)
            }, true)

            return if (oldDescriptor.capacity == oldDescriptor.size) {
                Core(
                    size = oldDescriptor.size + 1,
                    capacity = oldDescriptor.capacity * 2,
                    leftToMove = oldDescriptor.capacity,
                    operation = operation,
                    memory = memory,
                    oldMemory = oldDescriptor.memory,
                )
            } else {
                copy(
                    oldDescriptor,
                    size = oldDescriptor.size + 1,
                    operation = operation
                )
            }
        }
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
