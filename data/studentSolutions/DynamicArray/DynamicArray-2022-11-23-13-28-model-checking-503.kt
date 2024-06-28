package mpp.dynamicarray

import kotlinx.atomicfu.*
import java.lang.Exception

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
    private val core: AtomicRef<Core<E>?> = atomic(Core(INITIAL_CAPACITY))
    private val newArray: AtomicRef<Core<E>?> = atomic(null)

    override fun get(index: Int): E {
        while(true)
        {
            val currentValue = core.value!!.get(index) // При нормальной работе в Core может быть null при ресайзе, поэтому проверяем, пока не станет значением
            if (currentValue != null)
                return currentValue
        }
    }

    override fun put(index: Int, element: E) {
        while(true)
        {
            val currentValue = core.value!!.get(index)
            if (currentValue != null && core.value!!.putCas(index, currentValue, element)) // При нормальной работе в Core может быть null при ресайзе, поэтому проверяем, пока не станет значением и CASим, чтобы не поменяли под носом
                return
        }
    }

    override fun pushBack(element: E) {
        while(true) {
            val currentCore = core.value
            if (!core.value!!.pushBack(element)) // Если массив в данный момент расширяется, то 100% не сработает, так как расширение происходит при переполнении
            {
                if(newArray.compareAndSet(null, Core(currentCore!!.capacity*2))) // Если вышел CAS на перекладывание, то перекладываем спокойно, так как newArray мы забрали себе
                {
                    for(i in 0..size)
                    {
                        var currentValue = currentCore.get(i)
                        if (!newArray.value!!.pushBack(currentValue))
                            throw Exception("Failed pushBack on new array")
                        while(true) {
                            if (currentCore.putCas(i, currentValue, null)) // Зануляем переброшенные ячейки. Если не получилось и значение поменялось, то обновляем новый вектор и пробуем ещё раз
                                break
                            else
                                currentValue = currentCore.get(i)
                                newArray.value!!.put(i, currentValue)
                        }
                    }
                    if(newArray.value!!.pushBack(element))
                    {
                        core.value = newArray.value
                        newArray.value = null
                        return
                    }
                    else
                    {
                        throw Exception("Failed PB after resize")
                    }
                }
            } else
                return // Если мы успешно положили во внутреннюю структуру, то мы справились и ничего больше не надо
        }
    }

    override val size: Int get() = core.value!!.size
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
    private val _size = atomic(0)
    private val _capacity = capacity

    val size: Int = _size.value
    val capacity: Int = _capacity

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index < size)
        return array[index].value as E
    }

    fun putCas(index: Int, expect: E?, value: E?): Boolean {
        require(index < size)
        return array[index].compareAndSet(expect, value)
    }

    fun put(index: Int, value: E?) {
        require(index < size)
        val oldValue = array[index].value
        if(!array[index].compareAndSet(oldValue, value))
            throw Exception("CAS failed in CORE")
    }

    fun pushBack(value: E): Boolean { // Через FAA пушим, если получается size > capacity, то отказываем
       val result = _size.getAndIncrement()
        if (_size.value > _capacity) {
            _size.value = _capacity
            return false
        }
        array[result].value = value
        return true
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME