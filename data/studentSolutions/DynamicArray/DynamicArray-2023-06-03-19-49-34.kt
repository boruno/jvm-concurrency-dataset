//package day4

import kotlinx.atomicfu.*

// This implementation never stores `null` values.
class DynamicArray<E: Any> {
    private val core = atomic(Core(capacity = 1)) // Do not change the initial capacity

    fun allocateNewCore() {
        val currentCore = core.value
        val newCore = Core(capacity = currentCore.capacity * 2).also { it.size.value = currentCore.capacity }
        currentCore.next.compareAndSet(currentCore, newCore)
        configureNextCore()
    }

    fun configureNextCore() {
        val currentCore = core.value
        val newCore = currentCore.next.value ?: return

        for (it in 0 until currentCore.array.size) {
            val value = currentCore.array[it].value ?: error("Should've been a value")

            val unwrappedValue = if (value is DynamicArray<*>.FrozenValue) {
                value.data
            } else {
                @Suppress("UNCHECKED_CAST")
                currentCore.array[it].compareAndSet(value, FrozenValue(value as E))
                value
            }

            newCore.array[it].compareAndSet(null, unwrappedValue)
        }

        core.compareAndSet(currentCore, newCore)
    }

    /**
     * Adds the specified [element] to the end of this array.
     */
    fun addLast(element: E) {
        // TODO: Implement me!
        // TODO: Yeah, this is a hard task, I know ...

        while (true) {
            val currentCore = core.value
            val curSize = currentCore.size.value

            if (curSize == currentCore.capacity) {
                allocateNewCore()
                continue
            }

            // TODO: you need to install the element and
            // TODO: increment the size atomically.
            // TODO: You are NOT allowed to use CAS2,
            // TODO: there is a more efficient and smarter solution!

            if (currentCore.array[curSize].compareAndSet(null, element)) {
                currentCore.size.compareAndSet(curSize, curSize + 1)
                break
            } else {
                currentCore.size.compareAndSet(curSize, curSize + 1)
            }
        }
    }

    /**
     * Puts the specified [element] into the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    fun set(index: Int, element: E) {
        while (true) {
            val curCore = core.value
            val curSize = curCore.size.value
            require(index < curSize) { "index must be lower than the array size" }
            // TODO: check that the cell is not "frozen"

            val value = curCore.array[index].value

            if (value is DynamicArray<*>.FrozenValue) {
                configureNextCore()
                continue
            } else {
                if (curCore.array[index].compareAndSet(value, element)) {
                    break
                }
            }
        }
    }

    /**
     * Returns the element located in the cell [index],
     * or throws [IllegalArgumentException] if [index]
     * exceeds the size of this array.
     */
    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            val curCore = core.value
            val curSize = curCore.size.value
            require(index < curSize) { "index must be lower than the array size" }
            // TODO: check that the cell is not "frozen",
            // TODO: unwrap the element in this case.

            val value = curCore.array[index].value

            if (value is DynamicArray<*>.FrozenValue) {
                configureNextCore()
                continue
            }

            return value as E
        }
    }

    private class Core(
        val capacity: Int
    ) {
        val array = atomicArrayOfNulls<Any?>(capacity)
        val size = atomic(0)
        val next = atomic<Core?>(null)
    }

//    private inner class Cell(
//        val data: E,
//    ) {
//        val isFrozen = atomic(false)
//    }

    inner class FrozenValue(val data: E)
}