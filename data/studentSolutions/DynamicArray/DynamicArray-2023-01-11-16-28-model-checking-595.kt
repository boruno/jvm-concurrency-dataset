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
    private val usingFlag = atomic(0)
    private val core = atomic(Core<E>(INITIAL_CAPACITY, INITIAL_SIZE))

    override fun get(index: Int): E {
        val cvsv = core.value.size.value
        if (index >= cvsv) throw IllegalArgumentException()
        else return core.value.getArray()[index].value!!.value
    }

    override fun put(index: Int, element: E) {
        var k = 0
        while (true) {
            if (k > index) k++
            val coreValCur = core.value
            if (index >= coreValCur.size.value) throw IllegalArgumentException()
            else {
                val curn = coreValCur.getArray()[index].value
                val nodeNew = Node(element)
                if (!curn!!.remFl) {
                    if (coreValCur.getArray()[index].compareAndSet(curn, nodeNew)) {
                        return
                    }
                }
            }
        }
    }

    override fun pushBack(element: E) {
        val q = 0
        while (true) {
            val coreValCur = core.value
            var k = q
            val c = 2
            val position = coreValCur.size.value
            if (k == c) k += c
            val nodeNew = Node(element)
            if (position >= coreValCur.capacity) {
                if (usingFlag.compareAndSet(expect = 0, update = 1)) {
                    val newCore = Core<E>(coreValCur.capacity * 2, position)
                    var i = 0
                    while (true) {
                        if (i == position) break
                        val curn = coreValCur.getArray()[i].value
                        val nrn = delNode(curn!!.value)
                        val coreCur = coreValCur.getArray()[i]
                        if (coreCur.compareAndSet(curn, nrn)) {
                            newCore.getArray()[i++].value = curn
                        }

                    }
                    core.compareAndSet(coreValCur, newCore)
                    usingFlag.value = 0
                }
            }
            var fl = false
            if (position < coreValCur.capacity)
                if (coreValCur.getArray()[position].compareAndSet(null, nodeNew)) {
                    fl = true
                }
            if (fl && coreValCur.size.compareAndSet(position, position + 1)) {
                return
            }
        }
    }

    override val size: Int get() = core.value.size.value
}

//private class Core<E>(
//    capacity: Int,
//) {
    //private val array = atomicArrayOfNulls<E>(capacity)
private class Core<E> constructor(c: Int, s: Int) {
    private val _size = atomic(0)

    //val size: Int = _size.value



    val capacity = c
    val k = 0
    val q = 1
    private val array = atomicArrayOfNulls<Node<E>>(c)
    val size = atomic(s)

    fun getArray(): AtomicArray<Node<E>?> {
        return array
    }
}

open class Node<E> (v: E) {
    open val remFl = false
    val value = v
}

class delNode<E>(v: E) : Node<E>(v) {
    override val remFl = true
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
private const val INITIAL_SIZE = 0