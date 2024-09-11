import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int): E =
        a[index]!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index]!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        var i1 = index1
        var exp1 = expected1
        var upd1 = update1

        var i2 = index2
        var exp2 = expected2
        var upd2 = update2


        if (i1 > i2) {
            var tmp = i1
            i1 = i2
            i2 = tmp

            var tmp2 = exp1
            exp1 = exp2
            exp2 = tmp2

            tmp2 = upd1
            upd1 = upd2
            upd2 = tmp2
        }

        val descriptor = Descriptor(i1, exp1, upd1, i2, exp2, upd2)
        // println("descriptor i1: ${descriptor.index1}, i2: ${descriptor.index2}")

        if (a[i1]!!.v.compareAndSet(exp1, descriptor)) {
            descriptor.complete()
        } else {
            descriptor.outcome.value = OutCome.FAIL
        }
        return descriptor.outcome.value == OutCome.SUCCESS
    }

    class Ref<E>(initialValue: E) {
        val v = atomic<Any?>(initialValue)

        fun cas(expected: E, update: E): Boolean {
            v.loop { cur ->
                when (cur) {
                    is AtomicArrayNoAba<*>.Descriptor<*> -> cur.complete()
                    expected -> {
                        if (v.compareAndSet(cur, update)) return true
                    }

                    else -> return false
                }
            }
        }

        var value: E
            get() {
                v.loop { cur ->
                    when (cur) {
                        is AtomicArrayNoAba<*>.Descriptor<*> -> cur.complete()
                        else -> return cur as E
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is AtomicArrayNoAba<*>.Descriptor<*> -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) return
                    }
                }
            }
    }

    inner class Descriptor<E>(
        val index1: Int, val expected1: E, val update1: E,
        val index2: Int, val expected2: E, val update2: E
    ) {
        val outcome = atomic(OutCome.UNDECIDED)
        fun complete() {
            if (outcome.value == OutCome.UNDECIDED && a[index2]!!.v.compareAndSet(expected2, this@Descriptor)) {
                outcome.value = OutCome.SUCCESS
                a[index1]!!.v.compareAndSet(this@Descriptor, update1)
                a[index2]!!.v.compareAndSet(this@Descriptor, update2)
            } else if (a[index2]!!.v.value === this@Descriptor) {
                outcome.value = OutCome.SUCCESS
                a[index1]!!.v.compareAndSet(this@Descriptor, update1)
                a[index2]!!.v.compareAndSet(this@Descriptor, update2)
            } else if (outcome.value != OutCome.SUCCESS) {
                a[index1]!!.v.compareAndSet(this@Descriptor, expected1)
                outcome.value = OutCome.FAIL
            }
        }
    }

    enum class OutCome {
        SUCCESS, UNDECIDED, FAIL
    }
}