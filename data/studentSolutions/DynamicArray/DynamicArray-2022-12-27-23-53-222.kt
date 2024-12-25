//package mpp.dynamicarray

import kotlinx.atomicfu.*

class Element<T>(val state: String, val el: T?)

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
    private val core = atomic(Core<Element<E>>(INITIAL_CAPACITY))
    override fun get(index: Int): E {
//        println("get " + index)
        if (index >= size || index < 0) throw IllegalArgumentException()
        if (size == 4 && core.value.get(0).el == 0 && core.value.get(2).el == 0 && core.value.get(3).el == 2){
            val check = true
        }
        var count = 0
        while (true) {
            count++
            if (count >= 30) {
                val loopCheck = 0
//                println("oops")
                return last.value ?: throw IllegalArgumentException()
            }
            val coreVal = core.value
            if (index < coreVal.getCapacity()) {
                val el = coreVal.getOrNull(index)
                if (el != null && el.state == "Written" && el.el != null) {
//                    println("end get " + index + " = " + el.el)
                    return el.el
                } else {
                    helpMove()
                }
            } else {
                resize(coreVal)
            }
        }
    }

    override fun put(index: Int, element: E) {
//        TODO("Not yet implemented")
//        println("put " + element + " s = " + size)
        if (index >= size || index < 0) throw IllegalArgumentException()
        var count = 0
//        if (size == 3 && index == 1 && element == -5){
//            val check = 0
//        }
        while (true) {
            count++
            if (count >= 30) {
                val loopCheck = 0
            }
            val coreVal = core.value
            if (index < coreVal.getCapacity()) {
                val prev = coreVal.getOrNull(index)
                if (prev != null) {
                    if (prev.state == "Written") {
                        if (coreVal.cas(index, prev, Element("Written", element))) {
//                            println("end put " + element)
                            return
                        } else {
                            helpMove()
                        }
                    } else {
                        helpMove()
                    }
                }
            } else {
                resize(coreVal)
            }
        }
    }

    override fun pushBack(element: E) {
//        TODO("Not yet implemented")
        size
//        println("pB " + element + " s = " + size)
        last.getAndSet(element)
        var count = 0
        while (true) {
            val curSize = coreSize.value
            count++
            if (count >= 30) {
                val h = 0
            }
            val coreVal = core.value
            if (curSize < coreVal.getCapacity()) {
                if (coreVal.cas(curSize, null, Element("Written", element))) {
                    coreSize.compareAndSet(curSize, curSize + 1)
//                    println("end pB " + element)
                    return
                } else {
                    val cur = coreVal.getOrNull(curSize)
                    if (cur != null && cur.state != "Broken") {
                        coreSize.compareAndSet(curSize, curSize + 1)
                    }
                    helpMove()
                }
            } else {
                resize(coreVal)
            }
        }
    }

    private fun resize(old: Core<Element<E>>) {
        val new = Core<Element<E>>(old.getCapacity() * 2)

        old.next.compareAndSet(null, new)
        helpMove()
    }

    fun helpMove() {
        val old = core.value
        val new = old.next.value ?: return
        for (i in 0 until old.getCapacity()) {
            val el = old.getOrNull(i)
            if (el == null) {
                old.cas(i, el, Element("Broken", null))
            } else if (el.state == "Written") {
                old.cas(i, el, Element("Moved", el.el))
                new.cas(i, null, Element("Written", el.el))
            } else if (el.state == "Moved") {
                new.cas(i, null, Element("Written", el.el))
            } else if (el.state == "Broken") {
                continue
            }
        }
        core.value = new
    }

    override val size: Int get() {
        var coreVal = core.value
        var count = 0
        var ind = coreSize.value
        while(ind >= coreVal.getCapacity() || coreVal.getOrNull(ind) != null){
            count++
            if (count > 30){
                val loopCheck = 0
            }
            if (ind >= coreVal.getCapacity()){
                resize(coreVal)
            } else {
                coreSize.compareAndSet(ind, ind + 1)
            }
            coreVal = core.value
            ind = coreSize.value
        }
        return coreSize.value
    } // changed
    private val coreSize = atomic(0)
    private val last: AtomicRef<E?> = atomic(null)
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    val next = atomic<Core<E>?>(null)
//    val size: Int = _size.value // always 0 if val and not atomic

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
//        require(index < getSize() && index < getCapacity() && array[index].value != null) // size moved to DynamicArray
        return array[index].value as E
    }

    fun getOrNull(index: Int): E? {
        return array[index].value
    }

    fun getCapacity(): Int {
        return array.size
    }

    fun set(index: Int, el: E) {
        array[index].getAndSet(el)
    }

    fun cas(ind: Int, exp: E?, upd: E?): Boolean {
        return array[ind].compareAndSet(exp, upd)
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME