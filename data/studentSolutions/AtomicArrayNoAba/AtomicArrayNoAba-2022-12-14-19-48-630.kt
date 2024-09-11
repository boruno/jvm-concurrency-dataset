import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    private inner class DcssDescriptor(
        val casDescriptor: CasDescriptor, outcome: Outcome
    ) {
        val outcome = atomic(outcome)
    }

    private inner class CasDescriptor(
        val index1: Int, val expectA: E, val updateA: E, val index2: Int, val expectB: E, val updateB: E, outcome: Any
    ) {
        val outcome = atomic(outcome)
    }

    private enum class Outcome {
        UNDEFINED, SUCCESS, FAILED;

        fun toBoolean() = when (this) {
            UNDEFINED -> null
            SUCCESS -> true
            FAILED -> false
        }
    }


    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            when (val item = a[index].value!!) {
                is AtomicArrayNoAba<*>.DcssDescriptor -> helpDcss(item)
                is AtomicArrayNoAba<*>.CasDescriptor -> helpCas(item)
                else -> return item as E
            }
        }
    }

    fun set(index: Int, value: E) {
        while (true) {
            when (val item = a[index].value!!) {
                is AtomicArrayNoAba<*>.DcssDescriptor -> helpDcss(item)
                is AtomicArrayNoAba<*>.CasDescriptor -> helpCas(item)
                else -> a[index].compareAndSet(item, value)
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            when (val item = a[index].value!!) {
                is AtomicArrayNoAba<*>.DcssDescriptor -> helpDcss(item)
                is AtomicArrayNoAba<*>.CasDescriptor -> helpCas(item)
                else -> {
                    if (item as E == expected) {
                        if (a[index].compareAndSet(expected, update)) {
                            return true
                        }
                    } else {
                        return false
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun helpCas(descriptor: Any): Boolean {
        descriptor as AtomicArrayNoAba<E>.CasDescriptor
        while (true) {
            val outcome: Outcome
            while (true) {
                when (val readOutcome = descriptor.outcome.value) {
                    is AtomicArrayNoAba<*>.DcssDescriptor -> helpDcss(readOutcome)
                    else -> {
                        outcome = readOutcome as Outcome
                        break
                    }
                }
            }
            outcome.toBoolean()?.let {
                val (toA, toB) = if (it) {
                    descriptor.updateA to descriptor.updateB
                } else {
                    descriptor.expectA to descriptor.expectB
                }
                a[descriptor.index1].compareAndSet(descriptor, toA)
                a[descriptor.index2].compareAndSet(descriptor, toB)
                return it
            }

            when (val item = a[descriptor.index2].value!!) {
                is AtomicArrayNoAba<*>.DcssDescriptor -> helpDcss(item)
                is AtomicArrayNoAba<*>.CasDescriptor -> {
                    if (item === descriptor) {
                        if (item.outcome.compareAndSet(Outcome.UNDEFINED, Outcome.SUCCESS)) {
                            return true
                        }
                    } else {
                        helpCas(item)
                    }
                }

                else -> {
                    val curB = item as E
                    if (curB != descriptor.expectB) {
                        if (descriptor.outcome.compareAndSet(outcome, Outcome.FAILED)) {
                            return false
                        } else {
                            continue
                        }
                    }
                    val dcssDescriptor = DcssDescriptor(
                        descriptor, Outcome.UNDEFINED
                    )
                    if (a[descriptor.index2].compareAndSet(item, dcssDescriptor)) {
                        if (helpDcss(dcssDescriptor)) {
                            if (descriptor.outcome.compareAndSet(outcome, Outcome.SUCCESS)) {
                                return true
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun helpDcss(descriptor: Any): Boolean {
        descriptor as AtomicArrayNoAba<E>.DcssDescriptor
        while (true) {
            when (val outcomeRead = descriptor.casDescriptor.outcome.value) {
                is AtomicArrayNoAba<*>.DcssDescriptor -> {
                    if (outcomeRead === descriptor) {
                        assert(false)
                    } else {
                        helpDcss(descriptor)
                    }
                }

                else -> {
                    descriptor.outcome.value.toBoolean()?.let {
                        val toB = if (it) {
                            descriptor.casDescriptor
                        } else {
                            descriptor.casDescriptor.expectB as Any
                        }
                        a[descriptor.casDescriptor.index2].compareAndSet(descriptor, toB)
                        return it
                    }

                    if (outcomeRead as Outcome !== Outcome.UNDEFINED) {
                        if (descriptor.outcome.compareAndSet(Outcome.UNDEFINED, Outcome.FAILED)) {
                            if (a[descriptor.casDescriptor.index2].compareAndSet(
                                    descriptor, descriptor.casDescriptor.expectB
                                )
                            ) {
                                return false
                            } else {
                                continue
                            }
                        } else {
                            continue
                        }
                    }

                    if (descriptor.outcome.compareAndSet(Outcome.UNDEFINED, Outcome.SUCCESS)) {
                        if (a[descriptor.casDescriptor.index2].compareAndSet(
                                descriptor, descriptor.casDescriptor
                            )
                        ) {
                            return true
                        }
                    }


                }
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 != expected2) {
                return false
            }
            return cas(index1, expected1, update2)
        }
        if (index1 > index2) {
            return cas2(index2, expected2, update2, index1, expected1, update1)
        }
        while (true) {
            when (val item = a[index1].value!!) {
                is AtomicArrayNoAba<*>.DcssDescriptor -> helpDcss(item)
                is AtomicArrayNoAba<*>.CasDescriptor -> helpCas(item)
                else -> {
                    val curA = item as E
                    if (curA != expected1) {
                        return false
                    }
                    val casDescriptor =
                        CasDescriptor(index1, expected1, update1, index2, expected2, update2, Outcome.UNDEFINED)
                    if (a[index1].compareAndSet(curA, casDescriptor)) {
                        return helpCas(casDescriptor)
                    }
                }
            }
        }

    }

}
