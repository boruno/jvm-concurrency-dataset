import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) {
            a[i].value = Ref()
            a[i].value!!.value = initialValue
        }
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E): Boolean {
        val exp = a[index].value
        if(exp!!.value == expected) {
            val upd = Ref<E>()
            upd.value = update
            return a[index].compareAndSet(exp, upd)
        }
        return false
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        val A = a[index1].value
        val B = a[index2].value
        val descriptor = CAS2Descriptor(A, B, update1, update2)
        descriptor.trySetExpectA(expected1)
        if(descriptor.status ==  OutcomeStatus.FAIL)
            return false
        descriptor.trySetExpectB(expected2)
        if(descriptor.status ==  OutcomeStatus.FAIL)
            return false
        descriptor.complete()
        return true
    }

}
enum class OutcomeStatus{
    UNDECIDED,
    DONE,
    FAIL
}

abstract class Descriptor{
    abstract fun complete()

    abstract var status: OutcomeStatus
}

class CAS2Descriptor<E>(
    val a: Ref<E>?, val b: Ref<E>?, val updateA: E?, val updateB: E?
) : Descriptor() {
    var expectA: E? = null
    var expectB: E? = null
    override fun complete() {
        if (a!!.value == expectA && b!!.value == expectB)
        {
            a.value = updateA!!
            b.value = updateB!!
        }
    }

    fun trySetExpectA(expectA: E){
        if(a!!.value == expectA) {
            this.expectA = expectA
            a._value.compareAndSet(a.value, this)
        }
        else
            status = OutcomeStatus.FAIL
    }

    fun trySetExpectB(expectB: E){
        if(b!!.value == expectB)
            if(status == OutcomeStatus.UNDECIDED)
            {
                this.expectB = expectB
                b._value.compareAndSet(b.value, this)
                status = OutcomeStatus.DONE
            }
            else
            {
                status = OutcomeStatus.FAIL
            }
        else
            status = OutcomeStatus.FAIL
    }


    override var status: OutcomeStatus = OutcomeStatus.UNDECIDED
}



class Ref<E>{
    val _value: AtomicRef<Any?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    var value: E
        get()  {
        _value.loop {
            cur -> when(cur){
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