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
    private val _size = atomic(0)

    override fun get(index: Int): E {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val current_core = core.value
            val element = current_core.get(index)
            if (element != null) {
                return element
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) {
            throw IllegalArgumentException()
        }

        while (true) {
            val current_core = core // текущее ядро
            val current_core_element = current_core.value.get(index)
            if (current_core_element != null) {
                if (current_core.value.CAS(index, current_core_element, element)) {
                    return
                }
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val current_core = core.value // запомнили текущее ядро
            val current_size = size // запомнили текущий размер

            if (current_size < current_core.getCapacity()) { // если размер массива еще меньше вместимости
                if (current_core.CAS(current_size, null, element)) { // пытаемся поменять нулл на элемент, если не получается - повторяем заново
                    if (_size.compareAndSet(current_size, current_size + 1)) { // пытаемся поменять размер, который запомнили
                        return
                    }
                    else { // если он отличается от запомненного - начинаем заново
                        current_core.CAS(current_size, element, null)
                        continue
                    }
                }
            }
            else if (current_size == current_core.getCapacity()) { // если текущий размер равен вместимости (кор заполнен полностью)
                var current_core_first_element = current_core.get(0) // запоминаем первый элемент

                while (current_core_first_element != null) { // если первый элемент нулл - значит массив уже переместили - выходим из цикла и повторяем заново
                    if (current_core.CAS(0, current_core_first_element, null)) { // если смогли поменять не нуллевый элемент - значит этот поток начал перемещать элементы
                        val new_core = Core<E>(current_core.getCapacity() * 2) // новый кор
                        new_core.CAS(0, null, current_core_first_element) // заполняем новй кор первым элементом
                        if (current_size > 1) { // если элементов больше 1 - перемещаем оставшиеся элементы
                            for (i in 1 until current_size) {
                                var current_core_element = current_core.get(i)
                                while (!current_core.CAS(i, current_core_element, null)) { // должны убедиться что переместили последнюю версию элемента
                                    current_core_element = current_core.get(i)
                                }
                                new_core.CAS(i, null, current_core_element)
                            }
                        }
                        if (_size.compareAndSet(current_size, current_size + 1)) { // увеличиваем размер - сейчас это капасити + 1, если что то пошло не так - начинаем заново
                            break
                        }
                        if (core.compareAndSet(current_core, new_core)) { // меняем предыдущее ядро на текущее
                            return
                        }
                        else { // если не смогли поменять ядро - начинаем заново
                            break
                        }
                    }
                    current_core_first_element = current_core.get(0) // обновляем текущий первый элемент, служит индикатором того, начали ли менять кор или нет
                }
            }
        }
    }

    override val size: Int get() = _size.value
}

//private class Element<E>(element: E? = null) {
//    val isBeingUsed = atomic(false) // используется ли сейчас данный элемент пушем или путом
//    val element = atomic(element) // хранит значение элемента, если элемент был перемещен пушем то устанавливается нулл
//}

private class Core<E>(
    capacity: Int,
) {
    private val array = atomicArrayOfNulls<E>(capacity)

    fun get(index: Int): E? {
        require(index < array.size)
        return array[index].value
    }

    fun CAS(index: Int, expected: E?, update: E?): Boolean{
        return array[index].compareAndSet(expected, update)
    }

    fun getCapacity(): Int {
        return array.size
    }
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME