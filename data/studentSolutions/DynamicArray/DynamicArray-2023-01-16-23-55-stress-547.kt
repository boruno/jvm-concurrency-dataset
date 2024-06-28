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
    private val totalSize = atomic(0)
    private val actualSize = atomic(0)

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

        core.value[index] = element
    }

    override fun pushBack(element: E) {
        val idx = totalSize.getAndIncrement()

        if (core.value.capacity > idx) {
            core.value[idx] = element
            return
        }

        val expanded = Core<E>(size * 2).apply { this[idx] = element }

        while (true) {
            if (core.value.next.compareAndSet(null, expanded)) {
                updateCore()
                break
            }

            updateCore()
        }
    }

    private fun updateCore() {
        val now = core.value
        val next = now.next.value ?: return

        repeat(now.capacity) {
            if (!next.array[it].compareAndSet(null, now[it])) {
                return
            }
        }

        while (core.value.next.value != null) {
            core.compareAndSet(core.value, core.value.next.value!!)
        }
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
        get() = totalSize.value
}

private class Core<E>(
    val capacity: Int
) {
    val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic<Core<E>?>(null)

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): E {
        return array[index].value as E
    }

    operator fun set(index: Int, e: E) {
        array[index].getAndSet(e)
    }

    fun expand(idx: Int): Core<E> {
        if (idx >= capacity) {
            val tmp = Core<E>(idx * 2)

            repeat(capacity) { k ->
                array[k].value?.let { tmp[k] = it }
            }

            return tmp.expand(idx)
        }

        return this
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
