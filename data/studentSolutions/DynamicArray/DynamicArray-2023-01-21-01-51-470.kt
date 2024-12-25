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
        var c = core.value
        while (true) {
            val v = c.array[index].value!!
            if (v is Value<E>) {
                return v.value
            } else if (v is Freezed<E>) {
                return v.value
            }
            c = c.next.value!!
        }
    }

    override fun put(index: Int, element: E) {
        var c = core.value
        while (true) {
            val v = c.array[index].value!!
            if (v is Value<E>) {
                if (c.set(index, v, Value(element))) return
            } else if (v is Freezed) {
                move(c)
            } else {
                c = c.next.value!!
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val cur = core.value
            val size = cur.size

            if (size < cur.capacity) {
                if (cur.set(size, Value(element))) {
                    cur.inc()
                    return
                }
                continue
            }

            val new = Core<E>(cur.capacity*2)
            cur.next.compareAndSet(null, new)
            move(cur)
        }
    }

    private fun move(c: Core<E>) {
        val next = c.next.value ?: return
        next.setSize(c.capacity)
        for (i in 0 until c.capacity) {
            while (true) {
                val v = c.array[i].value!!
                if (v is Moved<E>) break
                if (v is Value<E>) {
                    val fixed = Freezed(v.value)
                    c.set(i, v, fixed)
                    next.set(i, Value(v.value))
                    c.set(i, fixed, Moved())
                    continue
                }
                if (v is Freezed<E>) {
                    next.set(i, Value(v.value))
                    c.set(i, v, Moved())
                }
            }
        }
        core.compareAndSet(c, next)
    }

    override val size: Int get() = core.value.size
}

sealed class Val<E>
class Value<E>(val value: E) : Val<E>()
class Freezed<E>(val value: E) : Val<E>()
class Moved<E> : Val<E>()

private class Core<E>(
    val capacity: Int,
) {
    val array = atomicArrayOfNulls<Val<E>>(capacity)
    private val _size = atomic(0)
    val next: AtomicRef<Core<E>?> = atomic(null)

    val size: Int get() = _size.value

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return (array[index].value as Value<E>).value
    }


    fun setSize(new: Int) {
        _size.compareAndSet(0, new)
    }

    fun inc() {
        val cur = _size.value
        _size.compareAndSet(cur, cur + 1)
    }

    fun set(index: Int, element: Val<E>): Boolean = array[index].compareAndSet(null, element)
    fun set(index: Int, expected: Val<E>, update: Val<E>): Boolean = array[index].compareAndSet(expected, update)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME
