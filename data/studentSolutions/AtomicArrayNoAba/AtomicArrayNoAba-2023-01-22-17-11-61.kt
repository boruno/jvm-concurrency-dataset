import kotlinx.atomicfu.*
import kotlinx.atomicfu.AtomicRef

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        while (true) {
            val res = a[index].value!!
            if (res is CAS2Descriptor<*>) {
                cas2help(res as CAS2Descriptor<E>)
            } else if (res is DCSSDescriptor<*>) {
                dcssHelp(res as DCSSDescriptor<E>)
            } else {
                return res as E
            }
        }
    }

    fun cas(index: Int, expected: Any, update: Any) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        indexA: Int, expectedA: E, updateA: E,
        indexB: Int, expectedB: E, updateB: E
    ): Boolean {
        if (indexA == indexB) {
            return a[indexA].compareAndSet(expectedA, (expectedA as Int + 2))
        }

        val cas2desc = CAS2Descriptor<E>(indexA, expectedA, updateA, indexB, expectedB, updateB)

        if (cas(indexA, expectedA as Any, cas2desc)) {
            val outcome = dcss(cas2desc.indexB, cas2desc.expectedB as Any, cas2desc, cas2desc.outcome.value, null)
            if (outcome) {
                cas2desc.outcome.compareAndSet(null, true)
                cas(cas2desc.indexA, cas2desc as Any, cas2desc.updateA as Any)
                cas(cas2desc.indexB, cas2desc as Any, cas2desc.updateB as Any)
                return cas2desc.outcome.value!!
            }
            if (cas2desc.outcome.compareAndSet(null, false)) {
                cas(cas2desc.indexA, cas2desc as Any, cas2desc.expectedA as Any)
            } else {
                cas(cas2desc.indexA, cas2desc as Any, cas2desc.updateA as Any)
                cas(cas2desc.indexB, cas2desc as Any, cas2desc.updateB as Any)
            }
            return cas2desc.outcome.value!!
        } else {
            cas2desc.outcome.compareAndSet(null, false)
            return false
        }
    }

    fun cas2help(caS2Descriptor: CAS2Descriptor<E>) {
        while (true) {
            if (caS2Descriptor.outcome.value == true) {
                cas(caS2Descriptor.indexA, caS2Descriptor as Any, caS2Descriptor.updateA as Any)
                cas(caS2Descriptor.indexB, caS2Descriptor as Any, caS2Descriptor.updateB as Any)
                return
            }
            if (caS2Descriptor.outcome.value == false) {
                cas(caS2Descriptor.indexA, caS2Descriptor as Any, caS2Descriptor.expectedA as Any)
                cas(caS2Descriptor.indexB, caS2Descriptor as Any, caS2Descriptor.expectedB as Any)
                return
            }
            while (true) {
                val res = a[caS2Descriptor.indexB].value!!
                if (res is DCSSDescriptor<*>) {
                    dcssHelp(res as DCSSDescriptor<E>)
                    if (res.outcome.value == true) {
                        if (caS2Descriptor.outcome.compareAndSet(null, true)) {
                            cas(caS2Descriptor.indexA, caS2Descriptor as Any, caS2Descriptor.updateA as Any)
                            cas(caS2Descriptor.indexB, caS2Descriptor as Any, caS2Descriptor.updateB as Any)
                            return
                        }
                    }
                    if (res.outcome.value == false) {
                        if (caS2Descriptor.outcome.compareAndSet(null, false)) {
                            cas(caS2Descriptor.indexA, caS2Descriptor as Any, caS2Descriptor.expectedA as Any)
                            cas(caS2Descriptor.indexB, caS2Descriptor as Any, caS2Descriptor.expectedB as Any)
                            return
                        }
                    }
                    continue
                } else if (res is CAS2Descriptor<*>) {
                    if (caS2Descriptor.outcome.compareAndSet(null, true)) {
                        cas(caS2Descriptor.indexA, caS2Descriptor as Any, caS2Descriptor.updateA as Any)
                        cas(caS2Descriptor.indexB, caS2Descriptor as Any, caS2Descriptor.updateB as Any)
                    }
                    return
                } else {
                    if (caS2Descriptor.outcome.value != null) break
                    else {
                        val outcome = dcss(
                            caS2Descriptor.indexB,
                            caS2Descriptor.expectedB as Any,
                            caS2Descriptor,
                            caS2Descriptor.outcome.value,
                            null
                        )
                        if (outcome) {
                            if (caS2Descriptor.outcome.compareAndSet(null, true)) {
                                cas(caS2Descriptor.indexA, caS2Descriptor as Any, caS2Descriptor.updateA as Any)
                                cas(caS2Descriptor.indexB, caS2Descriptor as Any, caS2Descriptor.updateB as Any)
                                return
                            }
                        } else {
                            break
                            if (caS2Descriptor.outcome.compareAndSet(null, false)) {
                                cas(caS2Descriptor.indexA, caS2Descriptor as Any, caS2Descriptor.expectedA as Any)
                                cas(caS2Descriptor.indexB, caS2Descriptor as Any, caS2Descriptor.expectedB as Any)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    fun dcss(
        indexA: Int, expectedA: Any, updateA: Any,
        valB: Boolean?, expectedB: Boolean?
    ): Boolean {
        val dcssDesc = DCSSDescriptor<E>(indexA, expectedA, updateA, valB, expectedB)
        if (cas(dcssDesc.indexA, dcssDesc.expectedA as Any, dcssDesc)) {
            if (dcssDesc.valB == dcssDesc.expectedB) {
                dcssDesc.outcome.compareAndSet(null, true)
            } else {
                dcssDesc.outcome.compareAndSet(null, false)
            }

            if (dcssDesc.outcome.value == true) {
                cas(dcssDesc.indexA, dcssDesc, dcssDesc.updateA as Any)
                return true
            } else {
                cas(dcssDesc.indexA, dcssDesc, dcssDesc.expectedA as Any)
                return false
            }
        } else {
            return false
        }
    }

    fun dcssHelp(dcssDescriptor: DCSSDescriptor<E>) {
        while (true) {
            if (dcssDescriptor.outcome.value == true) {
                cas(dcssDescriptor.indexA, dcssDescriptor, dcssDescriptor.updateA as Any)
                return
            }
            if (dcssDescriptor.outcome.value == false) {
                cas(dcssDescriptor.indexA, dcssDescriptor, dcssDescriptor.expectedA as Any)
                return
            }
            while (true) {
                val res = a[dcssDescriptor.indexA].value!!
                if (res is DCSSDescriptor<*>) {
                    if (dcssDescriptor.valB == dcssDescriptor.expectedB) {
                        dcssDescriptor.outcome.compareAndSet(null, true)
                    } else {
                        dcssDescriptor.outcome.compareAndSet(null, false)
                    }

                    if (dcssDescriptor.outcome.value == true) {
                        cas(dcssDescriptor.indexA, dcssDescriptor, dcssDescriptor.updateA as Any)
                    } else {
                        cas(dcssDescriptor.indexA, dcssDescriptor, dcssDescriptor.expectedA as Any)
                    }
                    return
                }
                else {
                    return
                }
            }
        }
    }

    class CAS2Descriptor<E>(
        val indexA: Int, val expectedA: E, val updateA: E,
        val indexB: Int, val expectedB: E, val updateB: E
    ) {
        val outcome: AtomicRef<Boolean?> = atomic(null) // null = undecided, true = success, false = fail
    }

    class DCSSDescriptor<E>(
        val indexA: Int, val expectedA: Any, val updateA: Any,
        val valB: Boolean?, val expectedB: Boolean?
    ) {
        val outcome: AtomicRef<Boolean?> = atomic(null) // null = undecided, true = success, false = fail
    }
}