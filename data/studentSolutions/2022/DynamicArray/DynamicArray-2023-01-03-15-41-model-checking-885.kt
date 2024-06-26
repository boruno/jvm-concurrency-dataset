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
//    private val core = atomic(Core<E>(INITIAL_CAPACITY))
    private val core = atomic(Core<Element<E>>(INITIAL_CAPACITY))
    private val _size = atomic(0)

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        return core.value.get(index).element.value as E
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val current_core = core // текущее ядро
            val current_core_element = current_core.value.get(index) // элемент текущего ядра

            if (current_core_element.isBeingUsed.compareAndSet(false, true)) { // пытаемся взять блокировку над элементом
                val current_core_element_value = current_core_element.element.value // если получается - запоминаем текущее значение элемента

                if (current_core_element_value != null) { // если элемент не нулл (не был перемещен)
                    if (current_core_element.element.compareAndSet(current_core_element_value, element)) { // если удается поменять на свое значение
                        return // пут выполнен
                    }
                    else {
                        current_core_element.isBeingUsed.compareAndSet(true, false) // если не удается поменять значение - то отпускаем блокировку и делаем все заново
                        continue
                    }
                }
                else {
                    current_core_element.isBeingUsed.compareAndSet(true, false) // если элемент нулл - то значит он был перемещен, отпускаем блокировку и делаем все заново
                    continue
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val current_core = core
            val current_size = size

            if (current_size + 1 < current_core.value.getCapacity()) { // если еще есть место для нового элемента
                val current_element = current_core.value.get(current_size + 1) // сохраняем текущий элемент

                if (current_element.isBeingUsed.compareAndSet(false, true)) { // устанавливаем флаг что он используется в данный момент
                    if (current_element.element.compareAndSet(null, element)) { // ожидаем нулл у элемента
                        current_element.isBeingUsed.compareAndSet(true, false) // отпускаем блокировку
                        return // успех
                    }
                    else {
                        current_element.isBeingUsed.compareAndSet(true, false) // отпускаем блокировку
                        continue // неудача
                    }
                }
            }
            else { // если места не осталось
                val current_core_first_element = current_core.value.get(0)
                var alreadyMoved = false

                while (!current_core_first_element.isBeingUsed.compareAndSet(false, true)) { // пытаемся взять блокировку над первым элементом
                    if (current_core_first_element.element.value == null) { // и одновременно смотрим чтобы первый элемент был не нулл
                        alreadyMoved = true
                        break
                    }
                }

                if (alreadyMoved) { // если мы вышли из цикла и при этом флаг сработал, то значит мы не взяли блокировку но элемент был перемещен
                    continue
                }

                // если мы вышли из цикла, а флаг не сработал, то значит мы взяли блокировку

                if (current_core_first_element.element.value == null) { // проверяем чтобы элемент был не нулл
                    current_core_first_element.isBeingUsed.compareAndSet(true, false) // если нул то отпускаем блокировку и повторяем все заново
                    continue
                }

                // если проверки прошли успешно, значит мы поставили блокировку над элементом и одновременно он не нулл - можно перемещать

                val new_core = Core<Element<E>>(current_core.value.getCapacity() * 2) // создаем новое ядро размера х2
                new_core.get(0).element.compareAndSet(null, current_core_first_element.element.value)
                current_core_first_element.element.compareAndSet(current_core_first_element.element.value, null)
                current_core_first_element.isBeingUsed.compareAndSet(true, false)

                if (current_size > 1) { // если размер больше одного
                    for (i in 1 until current_size) { // перемещаем остальные елементы
                        val current_element = current_core.value.get(i)

                        while (!current_element.isBeingUsed.compareAndSet(false, true)) {} // пытаемся взять блокировку пока не возьмем
                        val current_element_value = current_element.element.value
                        new_core.get(i).element.compareAndSet(null, current_element_value)
                        current_element.element.compareAndSet(current_element_value, null)
                        current_element.isBeingUsed.compareAndSet(true,false)
                    }
                }

                core.compareAndSet(current_core.value, new_core) // устанавливаем новое ядро
                return
            }
        }
    }

//    override val size: Int get() = core.value.size
    override val size: Int get() = _size.value
}

private class Element<E>(element: E? = null) {
    val isBeingUsed = atomic(false) // используется ли сейчас данный элемент пушем или путом
    val element = atomic(element) // хранит значение элемента, если элемент был перемещен пушем то устанавливается нулл
}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)
//    private val _size = atomic(0)
//
//    val size: Int = _size.value
//    val capacity: Int = array.size

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
//        require(index < size)
        require(index < array.size)
        return array[index].value as E
    }

    fun getCapacity(): Int {
        return array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME