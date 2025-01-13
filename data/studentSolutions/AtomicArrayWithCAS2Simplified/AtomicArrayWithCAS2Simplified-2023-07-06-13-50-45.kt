//package day3

import AtomicArrayWithCAS2Simplified.Status.*
import kotlinx.atomicfu.*
import java.lang.Exception
import kotlin.time.measureTimedValue


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = atomicArrayOfNulls<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i].value = initialValue
        }
    }

    fun get(index: Int): E {
        // TODO: the cell can store CAS2Descriptor
//        return array[index].value as E
        val value = array[index].value
        if (value is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            if (value.status.value == SUCCESS) {
                if(index == value.index1) {
                    return value.update1 as E
                } else if(index == value.index2) {
                    return value.update2 as E
                }
                // return update
            } else {
                if(index == value.index1) {
                    return value.expected1 as E
                } else if(index == value.index2) {
                    return value.expected2 as E
                }
                // return expected
            }
        } else {
            return value as E
        }
//        return array[index].value as E

        throw Exception("asd")
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        // TODO: this implementation is not linearizable,
        // TODO: use CAS2Descriptor to fix it.
//        if (array[index1].value != expected1 || array[index2].value != expected2) return false
//        array[index1].value = update1
//        array[index2].value = update2
//        return true
        val descriptor = if (index1 < index2) {
            CAS2Descriptor(index1, expected1, update1, index2, expected2, update2)
        } else {
            CAS2Descriptor(index2, expected2, update2, index1, expected1, update1)
        }
//        if(array[index1].compareAndSet(expected1, descriptor)) {
//            if(array[index2].compareAndSet(expected2, descriptor)) {
//                descriptor.status.compareAndSet(UNDECIDED, SUCCESS)
//                // apply
//                array[index1].compareAndSet(descriptor, update1)
//                array[index2].compareAndSet(descriptor, update2)
//
//                return true
//            } else {
//                descriptor.status.compareAndSet(UNDECIDED, FAILED)
//                array[index1].compareAndSet(descriptor, expected1)
////                array[index2].compareAndSet(descriptor, expected2)
//                return false
//            }
//        } else {
//            descriptor.status.compareAndSet(UNDECIDED, FAILED)
//            return false
//        }
        return descriptor.apply()
    }

    inner class CAS2Descriptor(
        val index1: Int,
        val expected1: E,
        val update1: E,
        val index2: Int,
        val expected2: E,
        val update2: E
    ) {
        val status = atomic(UNDECIDED)

        fun apply():Boolean {
            // TODO: install the descriptor, update the status, update the cells.
//            array[index1].value?.checkWorkFinished() ?: return true
            if(status.value === SUCCESS) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return true
            }
            if(array[index1].value === this && array[index2].value === this) {
                array[index1].compareAndSet(this, update1)
                array[index2].compareAndSet(this, update2)
                return true
            }

            while (true) {
                val value1 = array[index1].value // берем первую ячейку


                if (value1 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) value1.apply() // если дескриптор, то выполняем
                else { // если не дескриптор, то значение
                    if (value1 !== expected1) { // если значение не подходящее, сразу выходим
                        status.compareAndSet(
                            UNDECIDED,
                            FAILED
                        )
                        return false
                    }
                    else {
                        if (array[index1].compareAndSet(expected1, this)) { // пробуем проставить первое поле
                            // получилось проставить первое поле, пытаемся проставить второе
                            while (true) {
                                val value2 = array[index2].value // вытащили вторую ячейку
                                if (value2 is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) value2.apply() // если дескриптор, то выполняем
                                else { // в ячейке значение
                                    if (value2 !== expected2) { // во второй ячейке ожидаемое значение?
                                        // откатываемся
                                        status.compareAndSet(
                                            UNDECIDED,
                                            FAILED
                                        )
                                        array[index1].compareAndSet(this, expected1)
                                        return false
                                    } else { // да, во второй ячейке ожидаемое значение
                                        if(array[index2].compareAndSet(expected2, this)) {
                                            status.compareAndSet(
                                                UNDECIDED,
                                                SUCCESS
                                            )
                                            array[index1].compareAndSet(this, update1)
                                            array[index2].compareAndSet(this, update2)
                                            return true
                                        } else { // что-то поменялось, идем на новый круг
                                            continue
                                        }
                                    }
                                }
                            }
                        } else { // проставить первое поле не получилось - идем на второй круг
                            continue
                        }
                    }
                }
            }

//            if (array[index1].compareAndSet(expected1, this)) {
////                array[index2].value?.checkWorkFinished() ?: return true
//                if(array[index2].compareAndSet(expected2, this)) {
//                    status.compareAndSet(
//                        UNDECIDED,
//                        SUCCESS
//                    )
//                    // apply
//                    array[index1].compareAndSet(this, update1)
//                    array[index2].compareAndSet(this, update2)
//
//                    return true
//                } else {
//                    status.compareAndSet(
//                        UNDECIDED,
//                        FAILED
//                    )
//
//                    array[index1].compareAndSet(this, expected1)
////                array[index2].compareAndSet(descriptor, expected2)
//                    return false
//                }
//            } else {
//                status.compareAndSet(
//                    UNDECIDED,
//                    FAILED
//                )
//                return false
//            }
        }
//        private fun Any.checkWorkFinished(): Boolean? {
//            if(this is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
//                if(this == this@CAS2Descriptor) return null
//                this.apply()
//                return true
//            } else {
//                return false
//            }
//        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}