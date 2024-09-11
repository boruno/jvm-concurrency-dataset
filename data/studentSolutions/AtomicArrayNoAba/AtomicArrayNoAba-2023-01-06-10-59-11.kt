import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val arr = atomicArrayOfNulls<Any?>(size)

    init {
        for (i in 0 until size) arr[i].value = initialValue
    }

    fun get(index: Int): E {
        while(true) {
            val e = arr[index].value
            if(isDescriptor(e))
                (e as Descriptor<E>).complete()
            else
                return e as E
        }
    }

    fun cas(index: Int, expected: Any?, update: Any?): Boolean {
        while(true) {
            val e = get(index)
            if (e != expected) return false
            if (arr[index].compareAndSet(expected, update)) return true
        }
    }

    fun isDescriptor(any: Any?): Boolean {
        return any is Descriptor<*>
    }

    fun cas2(index1: Int, expected1: E, update1: E, index2: Int, expected2: E, update2: E): Boolean {
        if(index1 == index2) {
            if(expected1 != expected2) return false
            return cas(index1, expected1, update2)
        }

        val descriptor =
            if(index1 < index2)
                Descriptor2(index1, expected1, update1, index2, expected2, update2)
            else
                Descriptor2(index2, expected2, update2, index1, expected1, update1)

        while(true) {
            val a = arr[descriptor.index1].value
            if(isDescriptor(a)) {
                (a as Descriptor<E>).complete()
                continue
            }
            val exp1 = descriptor.expected1
            if(a == exp1) {
                if(arr[descriptor.index1].compareAndSet(exp1, descriptor)) {
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
                        arr[index1].compareAndSet(this, expected1)
                        arr[index2].compareAndSet(this, expected2)
                        return
                    }
                    Outcome.SUCCESS -> {
                        arr[index1].compareAndSet(this, update1)
                        arr[index2].compareAndSet(this, update2)
                        return
                    }
                    else -> {
                        val b = arr[index2].value

                        if(b == this) {
                            outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                            continue
                        }

                        if(isDescriptor(b)) {
                            (b as Descriptor<E>).complete()
                        } else {
                            if(b == expected2) {
                                val desc = DescriptorDCSS(index2, expected2, this)
                                if(arr[index2].compareAndSet(expected2, desc)) desc.complete()
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

    inner class DescriptorDCSS<E>(val index: Int, val expected: E, val desc: Descriptor2<E>): Descriptor<E>() {
        override fun complete() {
            val update: Any? = if (desc.outcome.value == Outcome.UNDECIDED) desc else expected
            arr[index].compareAndSet(this, update)
        }
    }

    abstract class Descriptor<E> {
        abstract fun complete()
    }
}