import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any?>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        while(true) {
            val e = a[index].value
            if(isDescriptor(e))
                (e as Descriptor2<E>).complete()
            else
                return e as E
        }
    }

    fun cas(index: Int, expected: Any?, update: Any?) =
        a[index].compareAndSet(expected, update)

    fun isDescriptor(any: Any?): Boolean {
        return any is Descriptor<*>
    }

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {

        var descriptor =
            if(index1 > index2)
                Descriptor2(index1, expected1, update1, index2, expected2, update2)
            else
                Descriptor2(index2, expected2, update2, index1, expected1, update1)

        while(true) {
            val i1 = a[descriptor.index1].value
            if(isDescriptor(i1)) {
                (i1 as Descriptor2<E>).complete()
                continue
            }
            val exp1 = descriptor.expected1
            if(i1 == exp1) {
                if(cas(descriptor.index1, exp1, descriptor)) {
                    descriptor.complete()
                    return descriptor.outcome.value == Outcome.SUCCESS
                }
            } else return false
        }
    }

    enum class Outcome {
        UNDECIDED, FAIL, SUCCESS
    }

    inner class Descriptor2<E>(val index1: Int, val expected1: E, val update1: E, val index2: Int, val expected2: E, val update2: E, val outcome: AtomicRef<Outcome> = atomic(Outcome.UNDECIDED)): Descriptor<E>() {
        override fun complete() {
            while(true) {
                when(outcome.value) {
                    Outcome.FAIL -> {
                        cas(index1,this, expected1)
                        cas(index2,this, expected2)
                        return
                    }
                    Outcome.SUCCESS -> {
                        cas(index1,this, update1)
                        cas(index2,this, update2)
                        return
                    }
                    Outcome.UNDECIDED -> {
                        val b = a[index2].value

                        if(b == this) {
                            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                            continue
                        }

                        if(isDescriptor(b)) {
                            val upd = if(outcome.value == Outcome.UNDECIDED) this else expected2
                            cas(index2,this, upd)
                        } else {
                            if(b == expected2) {
                                if(cas(index2, expected2, this)) {
                                    val upd = if(outcome.value == Outcome.UNDECIDED) this else expected2
                                    cas(index2,this, upd)
                                }
                            } else {
                                outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                                continue
                            }
                        }
                    }
                }
            }
        }
    }

    abstract class Descriptor<E> {
        abstract fun complete()
    }
}