@file:Suppress("BooleanLiteralArgument", "UNCHECKED_CAST")

package mpp.dynamicarray

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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val index = atomic(0)

    init {
        println("#")
    }

    override fun get(index: Int): E {
        val now = updateCore()

        require(index in 0 until size) { "index `$index` is out of bounds" }

        return (now[index] as? Core<E>.Ok)?.e ?: get(index)
    }

    override fun put(index: Int, element: E) {
        val now = updateCore()

        require(index in 0 until size) { "index `$index` is out of bounds" }

        (now.set(index, element) as? Core<E>.Ok) ?: put(index, element)
    }

    override fun pushBack(element: E) {
        val idx = index.value

        expand(idx)

        val now = updateCore()

        if (now.array[idx].compareAndSet(null, Just(element))) {
            index.compareAndSet(idx, idx + 1)
        } else {
            index.compareAndSet(idx, idx + 1)
            pushBack(element)
        }
    }

    private fun expand(index: Int) {
        val now = core.value
        if (index >= core.value.capacity) {
            if (!now.next.compareAndSet(null, Core<E>(now.capacity * 2))) {
                updateCore()
                expand(index)
                return
            }
        }
    }

    private fun updateCore(): Core<E> {
        val now = core.value
        val next = now.next.value

        if (next == null) {
            return now
        } else if (next.completed.value) {
            if (core.compareAndSet(now, next)) {
                return next
            } else {
                return updateCore()
            }
        }

        repeat(now.capacity) {
            next.array[it].compareAndSet(null, Moved((now[it] as Core<E>.Ok).e))
        }

        core.compareAndSet(now, next)

        next.completed.compareAndSet(false, true)

        return updateCore()
    }

    override val size: Int
        get() = index.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Wrapper<E>>(capacity)
    val next = atomic<Core<E>?>(null)
    val completed = atomic(false)

    sealed interface Result
    inner class Ok(val e: E) : Result
    inner class Fail() : Result


    operator fun get(index: Int): Result {
        if (array.size <= index) {
            return Fail()
        }

        return when (val v = array[index].value) {
            is Just<E> -> Ok(v.e)
            is Fixed<E> -> Ok(v.e)
            is Moved<E> -> Ok(v.e)
            else -> Fail()
        }
    }

    operator fun set(index: Int, e: E): Result {
        if (array.size <= index) {
            return Fail()
        }

        return when (array[index].value) {
            null, is Deleted<E>, is Fixed<E> -> Fail()
            else -> {
                val v = array[index].getAndSet(Just(e))
                println("set($index, $e)> $v : ${Ok(e)}")
                Ok(e)
            }
        }
    }
}

interface Wrapper<E>
class Just<E>(val e: E) : Wrapper<E> {
    override fun toString() = "Just($e)"
}
class Fixed<E>(val e: E) : Wrapper<E> {
    override fun toString() = "Fixed($e)"
}
class Moved<E>(val e: E) : Wrapper<E> {
    override fun toString() = "Moved($e)"
}
class MovedEmpty<E>() : Wrapper<E> {
    override fun toString() = "MovedEmpty()"
}
class Deleted<E>() : Wrapper<E> {
    override fun toString() = "Deleted()"
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
