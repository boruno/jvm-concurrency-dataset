import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i].value?.value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E): Boolean {
        val exp = Ref<E>()
        exp.value = expected
        val upd = Ref<E>()
        upd.value = update
        return a[index].compareAndSet(exp, upd)
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
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
}

class Ref<E>{
    val _value: AtomicRef<Any?> = atomic(null)

    @Suppress("UNCHECKED_CAST")
    var value: E
        get() {
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