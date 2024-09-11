import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        val e = a[index].value
        while (e is CASDescriptor) continue
        return e as E
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

        return cas2(
            CASDescriptor(index1, expected1, update1,
            index2, expected2, update2)
        )
    }

    fun cas2(d: CASDescriptor) : Boolean {
        if (d.status.value == Result.UNDECIDED) {
            var status = Result.SUCCESS
            while (true) {
                val r = rdcss(Descriptor(d.status.value, Result.UNDECIDED, d.index1, d.expected1, d))
                if (r is CASDescriptor) {
                    if (r != d) {
                        cas2(r)
                        continue
                    } else {
                        break
                    }
                } else if (r != d.expected1) {
                    status = Result.FAIL
                    break
                }
            }
            if (status == Result.SUCCESS) {
                while(true) {
                    val r = rdcss(Descriptor(d.status.value, Result.UNDECIDED, d.index2, d.expected2, d))
                    if (r is CASDescriptor) {
                        if (r != d) {
                            cas2(r)
                            continue
                        } else {
                            break
                        }
                    } else if (r != d.expected2) status = Result.FAIL
                }
            }
            d.status.compareAndSet(Result.UNDECIDED, status)
        }
        val success: Boolean = d.status.value == Result.SUCCESS
        val u1 = if (success) d.update1 else d.expected1
        val u2 = if (success) d.update2 else d.expected2
        a[d.index1].compareAndSet(d, u1)
        a[d.index2].compareAndSet(d, u2)
        return success
    }

    class Descriptor(val value1: Any, val expected1: Any,
                        val index2: Int, val expected2: Any, val update2: Any)

    class CASDescriptor(val index1: Int, val expected1: Any,val update1: Any,
                           val index2: Int, val expected2: Any, val update2: Any,
                           val status: AtomicRef<Result> = atomic(Result.UNDECIDED))
    fun rdcss(d: Descriptor): E {
        var r: Any?
        do {
            val i2 = d.index2
            val elem = a[i2]
            r = if (a[i2].compareAndSet(d.expected2, d)) {
                d
            } else {
               a[i2].value
            }
            if (r is Descriptor) complete(r)
        } while (r is Descriptor)
        if (r == d.expected2) complete(d);
        return r as E

    }

    fun complete(d: Descriptor) {
        val v = d.value1
        if (v == d.expected1) a[d.index2].compareAndSet(d, d.update2)
        else a[d.index2].compareAndSet(d, d.expected2)
    }

    enum class Result {
        UNDECIDED, SUCCESS, FAIL
    }

}