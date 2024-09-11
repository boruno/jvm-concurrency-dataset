@file:Suppress("BooleanLiteralArgument")

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
        println("asd")
    }

    override fun get(index: Int): E {
        require(index in 0 until size) { "index `$index` is out of bounds" }

        updateCore()

        return core.value[index]
    }

    override fun put(index: Int, element: E) {
        require(index in 0 until size) { "index `$index` is out of bounds" }

        updateCore()

        core.value[index, false] = element
    }

    override fun pushBack(element: E) {
        val idx = index.getAndIncrement()

        val now = core.value
        if (idx >= size) {
            if (!now.next.compareAndSet(null, Core<E>(now.capacity * 2).apply { this.total.getAndSet(idx) })) {
                updateCore()
                pushBack(element)
                return
            }
        }

        updateCore()

        core.value[idx, true] = element
    }

    private fun updateCore() {
        val now = core.value
        val next = now.next.value ?: return

        repeat(now.size) {
            if (next.completed.value) {
                return
            }

            next.array[it].compareAndSet(null, now[it])
        }

        next.completed.compareAndSet(false, true)

        core.compareAndSet(now, next)
    }

//    override fun get(index: Int): E {
//        require(index in 0 until size) { "index `$index` is out of bounds" }
//
//        @Suppress("ControlFlowWithEmptyBody")
//        while (index >= actualSize.value);
//
//        return core.value[index]
//    }
//
//    override fun put(index: Int, element: E) {
//        require(index in 0 until size) { "$index is out of bounds" }
//
//        @Suppress("ControlFlowWithEmptyBody")
//        while (index >= actualSize.value);
//
//        core.value[index] = element
//    }
//
//    override fun pushBack(element: E) {
////        println("pb>$element")
//
//        val idx = arraySize.getAndIncrement()
////        println("idx($element)> $idx")
//
//        while (true) {
//            val now = core.value
//
//            val update = now.expand(idx)
//
////            println("upd-cap($element)> ${update.capacity}")
////            println("now-core($element)> ${core.value.capacity}")
//
//            if (core.value.capacity < update.capacity && !core.compareAndSet(now, update)) {
//                continue
//            }
//
//            core.value[idx] = element
//            actualSize.incrementAndGet()
//            return
//        }
//    }

    override val size: Int
        get() = core.value.size
}

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic<Core<E>?>(null)
    val total = atomic(0)
    val completed = atomic(false)

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E {
        return array[index].value as E
    }

    operator fun set(index: Int, inc: Boolean, e: E) {
        if (inc) total.incrementAndGet()

        array[index].getAndSet(e)
    }

    val size get() = total.value
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
