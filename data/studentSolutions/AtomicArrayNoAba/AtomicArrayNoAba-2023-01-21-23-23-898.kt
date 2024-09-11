import kotlinx.atomicfu.*

class AtomicArrayNoAba(size: Int, initialValue: Int) {
    private val a = atomicArrayOfNulls<Ref<Int>>(size)

    init {
        for (i in 0 until size) {
            a[i].compareAndSet(null, Ref(initialValue))
        }
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: Int, update: Int):Boolean {
        return a[index].value!!._value.compareAndSet(expected, update)
    }


    fun cas2(index1: Int, expected1: Int, update1: Int,
             index2: Int, expected2: Int, update2: Int
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if(index1 == index2){
            return cas(index1, update1, expected2 + 2)
        }
        val descriptor = CAS2Descriptor(a[index1].value!!, a[index2].value!!, expected1, update1, expected2, update2 )
        descriptor.complete()
        if(descriptor.st.value == CAS2Descriptor.status.COMPLETE)
            return true
        else
            return false
    }
}

abstract class Descriptor{
    abstract fun complete()
}

class CAS2Descriptor<E>(val A: Ref<E>, val B: Ref<E>, val expectA: E, val updateA: E, val expectB: E, val updateB: E): Descriptor(){
    override fun complete() {
        if (st.value == status.UNDECIDED) {
            if (A._value.value is Descriptor) {
                if(A._value.value != this)
                    A.value
            } else if (A.value != expectA) {
                st.compareAndSet(status.UNDECIDED, status.FAIL)
            } else {
                A._value.compareAndSet(expectA, this)
            }
            if (B._value.value is Descriptor) {
                if(B._value.value != this)
                    B.value
            } else if (B.value != expectB) {
                st.compareAndSet(status.UNDECIDED, status.FAIL)
            } else {
                st.compareAndSet(status.UNDECIDED, status.COMPLETE)
                B._value.compareAndSet(expectB, this)
            }
        }

        if (st.value == status.COMPLETE) {
            A._value.compareAndSet(this, updateA)
            B._value.compareAndSet(this, updateB)
        } else
        {
            A._value.compareAndSet(this, expectA)
            B._value.compareAndSet(this, expectB)
        }
    }

    enum class status{
        UNDECIDED,
        FAIL,
        COMPLETE,
    }

    val st: AtomicRef<status> = atomic(status.UNDECIDED)
}

class Ref<E>(val initial: E) {
    val _value: AtomicRef<Any?> = atomic(initial)

    var value: E
        get() {
            _value.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> return cur as E
                }
            }
        }
        set(upd) {
            _value.loop { cur ->
                when (cur) {
                    is Descriptor -> cur.complete()
                    else -> if (_value.compareAndSet(cur, upd))
                        return
                }
            }
        }
}