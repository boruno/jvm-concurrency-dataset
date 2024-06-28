@file:Suppress("DuplicatedCode", "UNCHECKED_CAST")

package day3

import day3.AtomicArrayWithCAS2Simplified.Status.*
import java.util.concurrent.atomic.*


// This implementation never stores `null` values.
class AtomicArrayWithCAS2Simplified<E : Any>(size: Int, initialValue: E) {
    private val array = AtomicReferenceArray<Any?>(size)

    init {
        // Fill array with the initial value.
        for (i in 0 until size) {
            array[i] = initialValue
        }
    }

    fun get(index: Int): E {
        val any = array[index]
        if(any is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor) {
            val s = any.status.get()
            if ( s == UNDECIDED || s == FAILED) {
                return if (index == any.lowerIndex)
                    any.lowerExpected as E
                else
                    any.upperExpected as E
            } else {
                if (index == any.lowerIndex) {
                    val r = any.lowerUpdate as E
                    array.compareAndSet(index, any, r)
                    return r
                } else
                {
                    val r = any.upperUpdate as E
                    array.compareAndSet(index, any, r)
                    return r
                }
            }
        } else
            return any as E
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        require(index1 != index2) { "The indices should be different" }
        val descriptor = CAS2Descriptor(
            index1 = index1, expected1 = expected1, update1 = update1,
            index2 = index2, expected2 = expected2, update2 = update2
        )
        descriptor.apply()
        return descriptor.status.get() === SUCCESS
    }

    inner class CAS2Descriptor(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E) {

        val status = AtomicReference(UNDECIDED)
        val lowerIndex: Int
        val lowerExpected: E
        val lowerUpdate: E
        val upperIndex: Int
        val upperExpected: E
        val upperUpdate: E

        init {
            val shouldRearrange = index2 < index1
            if(shouldRearrange)
            {
                lowerIndex = index2
                lowerExpected = expected2
                lowerUpdate = update2
                upperIndex = index1
                upperExpected = expected1
                upperUpdate = update1
            }
            else
            {
                lowerIndex = index1
                lowerExpected = expected1
                lowerUpdate = update1
                upperIndex = index2
                upperExpected = expected2
                upperUpdate = update2
            }

        }

        private fun updateStatus() {

            if(array.compareAndSet(lowerIndex, this, this)) {
                if (array.compareAndSet(upperIndex, this, this)) {
                    status.compareAndSet(UNDECIDED, SUCCESS)
                }
            }
            status.compareAndSet(UNDECIDED, FAILED)
        }

        fun apply() {
            // TODO: Install the descriptor, update the status, and update the cells;
            // TODO: create functions for each of these three phases.


            installDescriptors()
            updateStatus()
            updateCells()
        }

        private fun updateCells()
        {
            if(status.compareAndSet(SUCCESS, SUCCESS))
            {
                array.compareAndSet(lowerIndex, this, lowerUpdate)
                array.compareAndSet(upperIndex, this, upperUpdate)
            }
            else if(status.compareAndSet(FAILED, FAILED))
            {
                array.compareAndSet(lowerIndex, this, lowerExpected)
                array.compareAndSet(upperIndex, this, upperExpected)
            }
        }

        private fun installDescriptors()
        {
            while (true)
            {
                if(status.get() != UNDECIDED) return

                val lower = array.get(lowerIndex)
                if(lower is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && lower != this)
                {
                    if(lower.status.get() == UNDECIDED)
                        lower.apply()
                    else
                        lower.updateCells()
                }

                val lowerValue =  array.get(lowerIndex)
                if(array.compareAndSet(lowerIndex, lowerExpected, this) || lowerValue == this)
                {
                    val upper = array.get(upperIndex)
                    if(upper is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor && upper != this )
                    {
                        if(upper.status.get() == UNDECIDED)
                            upper.apply()
                        else
                            upper.updateCells()
                    }

                    val upperValue = array.get(upperIndex)
                    if(array.compareAndSet(upperIndex, upperExpected, this) || upperValue  == this)
                        return
                    else if(upperValue !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor /*&& upperValue != upperExpected*/)
                    {
                        if(array.compareAndSet(upperIndex, upperValue, upperValue))
                            return
                    }
                }
                else if(lowerValue !is AtomicArrayWithCAS2Simplified<*>.CAS2Descriptor/* && lowerValue != lowerExpected*/)
                {
                    if(array.compareAndSet(lowerIndex, lowerValue, lowerValue))
                        return
                }
            }
        }
    }

    enum class Status {
        UNDECIDED, SUCCESS, FAILED
    }
}