import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) {
            a[i].compareAndSet(null, Ref(initialValue))
        }
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E):Boolean {
        return a[index].value!!._value.compareAndSet(expected, update)
    }


    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

            val descriptor = CAS2Descriptor(a[index1].value!!, a[index2].value!!, expected1, update1, expected2, update2 )
            descriptor.complete()
            if(descriptor.st == CAS2Descriptor.status.COMPLETE)
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
        if(st == status.COMPLETE && (B._value.value is Descriptor|| A._value.value is Descriptor)){
            A._value.compareAndSet(this, updateA)
            B._value.compareAndSet(this, updateB)
            return
        }
        if(A._value.value !is Descriptor && A.value != expectA) {
            st = status.FAIL
            return
        }
        A._value.compareAndSet(expectA,this)
        if(st == status.UNDECIDED) {
            if (B.value != expectB && st != status.COMPLETE)
                st = status.FAIL
            else {
                st = status.COMPLETE
                B._value.compareAndSet(expectB, this)
            }
        }
        if(st == status.COMPLETE) {
            A._value.compareAndSet(this, updateA)
            B._value.compareAndSet(this, updateB)
        }
        if(st == status.FAIL){
            A._value.compareAndSet(this,expectA)
        }
    }

    enum class status{
        UNDECIDED,
        FAIL,
        COMPLETE,
        COMPLETED
    }

    var st = status.UNDECIDED
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