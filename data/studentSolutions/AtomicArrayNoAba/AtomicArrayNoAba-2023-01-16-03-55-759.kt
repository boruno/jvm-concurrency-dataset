import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(val size: Int, initialValue: E) {
    //private val a = atomicArrayOfNulls<Ref<E>?>(size)
    private val a = Array(size){Ref(initialValue)}
    /*init {
        for (i in 0..size) a[i].value = Ref(initialValue)
    }*/
    class Ref<E>(init: E) {
        val inValue: AtomicRef<Any?> = atomic(init)
        var value: E
            get() {
                while (true) {
                    when (val cur = inValue.value) {
                        is CASDescriptor<*> -> {
                            cur.complete()
                            continue
                        }
                        else -> return cur as E
                    }
                }
            }
            set(upd) {
                while (true) {
                    when (val cur = inValue.value) {
                        is CASDescriptor<*> -> {
                            cur.complete()
                            continue
                        }
                        else -> if (inValue.compareAndSet(cur, upd)) return
                    }
                }
            }
    }

    fun get(index: Int): E = a[index].value

    fun cas(index: Int, expected: E, update: E) =
        a[index].inValue.compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

        return CASDescriptor(a[index1], expected1, update1,
            a[index2], expected2, update2).complete()
    }



    abstract class Descriptor<E>(var status: Result) {
        abstract fun complete() : E
    }

    class RDCSSDescriptor<A, B> (val value1: Ref<A>, val expected1: A,
                           val value2: Ref<B>, val expected2: B, val update2: Any):
        Descriptor<Boolean>(Result.UNDECIDED) {
        override fun complete(): Boolean{
            val update = if (value1.value === expected1) update2 else  {
                expected2
                status = Result.FAIL
            }
            status = if (value2.inValue.compareAndSet(expected2, update) && status == Result.UNDECIDED)
                Result.SUCCESS else Result.FAIL
            return status == Result.SUCCESS
        }
    }
    class CASDescriptor<E>(val value1: Ref<E>, val expected1: E,val update1: E,
                           val value2: Ref<E>, val expected2: E, val update2: E): Descriptor<Boolean>(Result.UNDECIDED) {
        override fun complete() : Boolean {
            if (status == Result.UNDECIDED) {
                var cstatus = Result.SUCCESS
                if (!RDCSSDescriptor(Ref(status), Result.UNDECIDED,
                        value1, expected1, this).complete()) {
                    cstatus = Result.FAIL
                }
                if (cstatus == Result.SUCCESS) {
                    if (!RDCSSDescriptor(Ref(status), Result.UNDECIDED,
                            value2, expected2, this).complete()) {
                        cstatus = Result.FAIL
                    }
                }
                status = cstatus
            }
            val success: Boolean = status == Result.SUCCESS
            val u1 = if (success) update1 else expected1
            val u2 = if (success) update2 else expected2
            value1.inValue.compareAndSet(this, u1)
            value2.inValue.compareAndSet(this, u2)
            return success
        }

    }
    enum class Result {
        UNDECIDED, SUCCESS, FAIL
    }
}