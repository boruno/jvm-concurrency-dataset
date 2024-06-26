import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) : E {
        while(true) {
            when(val cur = a[index].value) {
                is AtomicArrayNoAba<*>.Descriptor -> {
                    cur.complete()
                    continue;
                }
                else -> return cur as E
            }
        }
    }

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        val i1 : Int
        val i2 : Int
        val e1 : E
        val e2 : E
        val u1 : E
        val u2 : E
        if (index1 > index2) {
            i1 = index2
            e1 = expected2
            u1 = update2
            i2 = index1
            e2 = expected1
            u2 = update1
        } else {
            i2 = index2
            e2 = expected2
            u2 = update2
            i1 = index1
            e1 = expected1
            u1 = update1
        }
        val descriptor = Descriptor(i1, e1, u1, i2, e2, u2)
        if (!a[i1].compareAndSet(e1, descriptor)) {
            return false;
        }
        return descriptor.complete();
    }

    inner class Descriptor(val i1 : Int, val e1 : E, val u1 : E,
                           val i2 : Int, val e2 : E, val u2 : E) {
        val outcome = atomic<Int>(UNDECIDED)

        fun complete() : Boolean{
            if (!a[i2].compareAndSet(e2, this)) {
                outcome.compareAndSet(UNDECIDED, FAILED);
            } else {
                outcome.compareAndSet(UNDECIDED, SUCCESS);
            }
            return if (outcome.value == FAILED) {
                a[i1].compareAndSet(this, e1);
                a[i2].compareAndSet(this, e2);
                false;
            } else {
                a[i1].compareAndSet(this, u1);
                a[i2].compareAndSet(this, u2);
                true;
            }
        }
    }
}

private const val UNDECIDED = 0;
private const val FAILED = 1;
private const val SUCCESS = 2;