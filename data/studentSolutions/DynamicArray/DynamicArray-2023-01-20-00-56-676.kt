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
        require(index < size)
        while (true) {
            val curCore = core.value
            try {
                return curCore.get(index)
            } catch (e: PrincessIsInAnotherCastleException) {
                core.compareAndSet(curCore, curCore.nextCore.value!!)
            }
        }
    }


    override fun put(index: Int, element: E) {
        require(index < size)
        while (true) {
            val curCore = core.value
            try {
                curCore.put(index, element)
                return
            } catch (e: PrincessIsInAnotherCastleException) {
                core.compareAndSet(curCore, curCore.nextCore.value!!)
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curCore = core.value
            try {
                curCore.pushBack(element)
                return
            } catch (e: PrincessIsInAnotherCastleException) {
                core.compareAndSet(curCore, curCore.nextCore.value!!)
            }
        }
    }

    override val size: Int get() = core.value.size.value
}

private class Core<E>(
    capacity_: Int,
) {
    private val capacity = capacity_
    private val array = atomicArrayOfNulls<Storage<E>>(capacity)
    val nextCore: AtomicRef<Core<E>?> = atomic(null)
    val size = atomic(0)

    constructor(capacity_: Int, size_ : Int) : this(capacity_) {
        size.compareAndSet(0, size_)
    }


    fun get(index: Int): E {

        while (true) {
            val curValue = array[index].value ?: continue
            if (curValue.type == StorageType.MOVED) {
                throw PrincessIsInAnotherCastleException("xdd")
            }
            return curValue.x
        }
    }

    fun put(index: Int, element: E) {
        while (true) {
            val curValue = array[index].value!!
            if (curValue.type == StorageType.VALUE) {
                if (array[index].compareAndSet(curValue, Storage(StorageType.VALUE, element))) {
                    return
                }
            } else {
                helpMove()
                throw PrincessIsInAnotherCastleException("lol")
            }
        }
    }

    fun pushBack(element: E) {
        while (true) {
            val curSize = size.value
            if (curSize < capacity) {
                if (array[curSize].compareAndSet(null,Storage(StorageType.VALUE, element))) {
                    size.compareAndSet(curSize, curSize + 1)
                    return
                } else {
                    size.compareAndSet(curSize, curSize + 1)
                }
            } else {
                nextCore.compareAndSet(null, Core(capacity * 2, curSize))
                helpMove()
                throw PrincessIsInAnotherCastleException("kek")
            }
        }
    }

    private fun helpMove() {
        val nextCore = nextCore.value!!
        for (i in 0 until capacity) {
            val curValue = array[i].value ?: continue
            if (curValue.type == StorageType.MOVED) continue
            if (curValue.type == StorageType.VALUE) {
                val curFixed = Storage(StorageType.FIXED, curValue.x)
                if (array[i].compareAndSet(curValue, curFixed)) {
                    nextCore.array[i].compareAndSet(null, curValue)
                    array[i].compareAndSet(curFixed, Storage(StorageType.MOVED, curValue.x))
                }
            } else {
                nextCore.array[i].compareAndSet(null, Storage(StorageType.VALUE, curValue.x))
                array[i].compareAndSet(curValue, Storage(StorageType.MOVED, curValue.x))
            }
        }
    }
}

private class Storage<E>(type_: StorageType, x_: E) {
    val type = type_
    val x = x_
}

private enum class StorageType {
    VALUE, FIXED, MOVED
}

private class PrincessIsInAnotherCastleException(message: String?) : Exception(message) {}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME