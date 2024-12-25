//package mpp.dynamicarray

import kotlinx.atomicfu.*

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

class DynamicArrayImpl<E : Any> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))


    override fun get(index: Int): E {
        while (true) {
            val value = core.value.get(index)

            if (value != null && (value is Value<E> || value is FixedValue<E>)) {
                return when (value) {
                    is FixedValue<E> -> value.value
                    is Value<E> -> value.value
                    else -> {
                        continue
                    }
                }
            }
            increase()
        }
    }

    override fun put(index: Int, element: E) {
        while (true) {
            val current = core.value
            val value = current.get(index)
            if (value != null && value is Value<E>) {
                if (current.array[index].compareAndSet(value, Value(element))) {
                    println("Finished put")
                    return
                }
                continue
            }

            increase()

        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val current = core.value
            val size = current.size()
            if (size < current.capacity) {
                if (current.array[size].compareAndSet(null, Value(element))) {
                    current.atomicSize.incrementAndGet()
                    println("Finished pushback")
                    return
                }
                continue
            }

            val next = current.next.value
            if (next == null) {
                val newNext = Core<E>(current.capacity * 2)
                newNext.atomicSize.getAndSet(current.capacity)
                current.next.compareAndSet(null, newNext)
            }
            increase()
        }

    }


    override val size: Int get() = core.value.size()


    private fun increase() {
        val current = core.value
        val next = current.next.value ?: return

        while (true) {

            val pos = next.copyCount.value
            if (pos >= current.size()) {
                core.compareAndSet(current, next)
                return
            }
            //println(pos)
            when (val value = current.get(pos)) {
                is OutDated<E> -> {
                    next.copyCount.compareAndSet(pos, pos + 1)
                }

                is FixedValue<E> -> {
                    next.array[pos].compareAndSet(null, value.ref)
                    current.array[pos].compareAndSet(value, OutDated())
                    next.copyCount.compareAndSet(pos, pos + 1)
                }

                is Value<E> -> {
                    if (current.array[pos].compareAndSet(value, FixedValue(value))) {
                        next.array[pos].compareAndSet(null, value)
                        current.array[pos].compareAndSet(value, OutDated())
                        next.copyCount.compareAndSet(pos, pos + 1)
                    }
                }
                else -> continue
            }
        }
    }
}

private class Core<E : Any>(
    val capacity: Int,
) {
    val next = atomic<Core<E>?>(null)
    val array = atomicArrayOfNulls<ArrayValue<E>>(capacity)
    val atomicSize = atomic(0)
    fun size(): Int = atomicSize.value
    val copyCount = atomic(0)

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): ArrayValue<E>? {
        require(index < size())
        return array[index].value
    }
}

sealed class ArrayValue<E>
class Value<E>(val value: E) : ArrayValue<E>() {

}

class FixedValue<E>(value: Value<E>) : ArrayValue<E>() {
    val ref = value
    val value = value.value

}

class OutDated<E> : ArrayValue<E>()

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME