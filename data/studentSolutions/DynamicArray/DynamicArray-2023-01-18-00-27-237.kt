@file:Suppress("BooleanLiteralArgument")

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

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val index = atomic(0)

    init {
        println("#".repeat(50))
    }

    override fun get(index: Int): E {
        println("get> $index")
        require(index in 0 until this.index.value) { "index `$index` is out of bounds" }

        updateCore()

        return core.value[index]
    }

    override fun put(index: Int, element: E) {
        println("put> $index : $element")
        require(index in 0 until this.index.value) { "index `$index` is out of bounds" }

        updateCore()

        core.value[index, false] = element
    }

    override fun pushBack(element: E) {
        println("${Thread.currentThread().id} : pb> $element | ${core.value.capacity}")
        val idx = index.getAndIncrement()

        fun pushBack(index: Int, element: E) {
            updateCore()

            val now = core.value
            if (index >= core.value.capacity) {
                if (!now.next.compareAndSet(null, Core<E>(now.capacity * 2))) {
                    updateCore()
                    pushBack(index, element)
                    return
                }
            }
        }

        pushBack(idx, element)

        println("${Thread.currentThread().id} : before-upd> $element | ${core.value.capacity}")
        updateCore()
        println("${Thread.currentThread().id} :  after-upd> $element | ${core.value.capacity}")

        core.value[idx, true] = element
    }

    private fun updateCore() {
        val now = core.value
        val next = now.next.value ?: return

        repeat(now.capacity) {
            if (next.completed.value) {
                return
            }

            next.array[it].compareAndSet(null, now[it])
        }

        next.completed.compareAndSet(false, true)

        core.compareAndSet(now, next)

        updateCore()
    }

    override val size: Int
        get() = index.value
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic<Core<E>?>(null)
    val completed = atomic(false)

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E {
        return array[index].value as E
    }

    operator fun set(index: Int, inc: Boolean, e: E) {
        array[index].getAndSet(e)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
