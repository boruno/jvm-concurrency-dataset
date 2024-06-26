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

        return CASDescriptor(
            arrayOf( PartOfCas<E>(a[index1], expected1, update1),
            PartOfCas(a[index2], expected2, update2))).complete()
    }



    abstract class Descriptor<E>(status: Result) {
        var status: Ref<Result> = Ref(status)
        abstract fun complete() : E
    }

    class RDCSSDescriptor<A, B> (val value1: Ref<A>, val expected1: A,
                           val value2: Ref<B>, val expected2: B, val update2: Any):
        Descriptor<Boolean>(Result.UNDECIDED) {
        override fun complete(): Boolean{
            val update = if (value1.value === expected1) update2 else  {
                expected2
                status.value = Result.FAIL
            }
            status.value = if (value2.inValue.compareAndSet(expected2, update) && status.value == Result.UNDECIDED)
                Result.SUCCESS else Result.FAIL
            return status.value == Result.SUCCESS
        }
    }
    class PartOfCas<E>(val value: Ref<E>, val expected: E,val update: E)
    class CASDescriptor<E>(val parts: Array<PartOfCas<E>>): Descriptor<Boolean>(Result.UNDECIDED) {
        override fun complete() : Boolean {
            if (status.value == Result.UNDECIDED) {
                var cstatus = Result.SUCCESS
                for (i in 0..1) {
                    if (cstatus != Result.SUCCESS) break
                    val cur = parts[i]
                    if(cur.value.inValue.value == this) {
                        continue
                    } else if (!RDCSSDescriptor(Ref(status), Result.UNDECIDED,
                            cur.value, cur.expected, this).complete()) {
                        cstatus = Result.FAIL
                    }
                }
                status.value = cstatus
            }
            val success: Boolean = status.value == Result.SUCCESS
            for (i in 0..1) {
                val u = if (success) parts[i].update else parts[i].expected
                parts[i].value.inValue.compareAndSet(this, u)
            }
            return success
        }

    }
    enum class Result {
        UNDECIDED, SUCCESS, FAIL
    }
}