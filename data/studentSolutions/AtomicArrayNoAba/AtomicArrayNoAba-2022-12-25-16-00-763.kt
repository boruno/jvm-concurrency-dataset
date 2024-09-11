import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicInteger

@Suppress("UNCHECKED_CAST")
class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): E {
        return when(val res = a[index].value!!) {
            is Descriptor<*> -> res.get() as E
            else -> res as E
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        cas2(index, expected, update, index, expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2 && (expected1 != expected2 || update1 != update2)) {
            return false
        } else if (index1 == index2) {
            return cas2(index1, expected1, ((update1 as Int) + 1) as E, index1, expected1, ((update1 as Int) + 1) as E)
        }

        var v1 = a[index1].value
//        println("v1: $v1")
        if (v1 is Descriptor<*> && (v1.st.compareAndSet(Status.INITIAL.ordinal, Status.FAILED.ordinal) ||
                    v1.st.get() == Status.FAILED.ordinal)) {
            a[index1].compareAndSet(v1, v1.cur)
            return false
        }

        var v2 = a[index2].value
//        println("v2: $v2")
        if (v2 is Descriptor<*> && (v2.st.compareAndSet(Status.INITIAL.ordinal, Status.FAILED.ordinal) ||
                    v2.st.get() == Status.FAILED.ordinal)) {
            a[index2].compareAndSet(v2, v2.cur)
            return false
        }

        val st = AtomicInteger(Status.INITIAL.ordinal)
        val res: Boolean

        if (v1 !is Descriptor<*> && v2 !is Descriptor<*>) {
            v1 = Descriptor(expected1, update1, st)
            v2 = Descriptor(expected2, update2, st)
            res = a[index1].compareAndSet(expected1, v1)
                    && (index1 == index2 || a[index2].compareAndSet(expected2, v2))
                    && st.compareAndSet(Status.INITIAL.ordinal, Status.SUCCESS.ordinal)
            if (!res) {
                st.compareAndSet(Status.INITIAL.ordinal, Status.FAILED.ordinal)
                return false
            }
        } else {
            res = false
        }

        if (v1 is Descriptor<*> && v1.st.get() == Status.SUCCESS.ordinal) {
            a[index1].compareAndSet(v1, v1.upd)
        }

        if (v2 is Descriptor<*> && v2.st.get() == Status.SUCCESS.ordinal) {
            a[index2].compareAndSet(v2, v2.upd)
        }

        return res
    }
}

enum class Status {
    INITIAL, FAILED, SUCCESS
}

class Descriptor<E>(val cur: E, val upd: E, val st: AtomicInteger) {



    fun get(): E {
        return when (st.get()) {
            Status.SUCCESS.ordinal -> upd
            else -> cur
        }
    }

    override fun toString(): String {
        return "Descriptor(cur=$cur, upd=$upd, st=$st)"
    }
}