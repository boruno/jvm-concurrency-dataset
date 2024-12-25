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
    override fun get(index: Int): E {
        if (index >= size || index < 0) throw IllegalArgumentException() // contract
        return core.value.get(index)
    }

    override fun put(index: Int, element: E) {
//        TODO("Not yet implemented")
//        println("put " + index + " " + element)
//        println("size = " + size + " cap = " + core.value.getCapacity())
        if (index >= size || index < 0) throw IllegalArgumentException()
        while (true) {
            val coreValue = core.value
            if (index < coreValue.getCapacity() /*&& coreValue.get(index) != null*/) {
                coreValue.set(index, element)
//                println("end put " + element)
                return
            }
        }
    }

    override fun pushBack(element: E) {
//        TODO("Not yet implemented")
//        println("pB " + element)
        val curSize = coreSize.getAndIncrement()
//        core.value.getAndIncrementSize()
        while (true) {
            val coreValue = core.value
            if (curSize < coreValue.getCapacity()) {
//                if (coreValue.cas(curSize, null, element)) { // ?
//                    println("end pB " + element)
                coreValue.cas(curSize, null, element) // if not null was already modified
                return
//                }
            } else {
                resize(coreValue)
            }
        }
    }

    private fun resize(old: Core<E>) {
        val new = Core<E>(old.getCapacity() * 2)

        if (old.next.compareAndSet(null, new)) {
//            new.setSize(coreSize.value)

            for (i in 0 until old.getCapacity()) {
                while (true) {
                    val el = old.getAndSet(i, null)
//                        println("get: " + el)
                    if (el != null) {
                        new.set(i, el)
                        break
                    }
                }
            }
            core.value = new
        }
    }

    override val size: Int get() = coreSize.value // changed
    private val coreSize = atomic(0)
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    //    private val _size = atomic(0)
    val next = atomic<Core<E>?>(null)
//    val size: Int = _size.value // always 0 if val and not atomic

    //    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E { // doesn't check size
//        require(index < getSize())
        while (true) {
            if (index < getCapacity()) {
                val res = array[index].value
                if (res != null){
                    return res
                }
            }
        }
    }

    fun getCapacity(): Int {
        return array.size
    }

    fun set(index: Int, el: E?) {
        array[index].getAndSet(el)
    }

    fun getAndSet(index: Int, el: E?): E? {
        return array[index].getAndSet(el)
    }

    fun cas(ind: Int, exp: E?, upd: E?): Boolean {
        return array[ind].compareAndSet(exp, upd)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME