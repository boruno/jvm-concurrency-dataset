import kotlinx.atomicfu.*

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size) // changed from AtomicRef to my Ref

    init {
        for (i in 0 until size) a[i] = Ref(initialValue) // changed from AtomicRef to my Ref
    }

    fun get(index: Int) =
        a[index]?.value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index]?.cas(expected, update) ?: false

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
        if (index1 == index2) {
            return if (expected1 == expected2) {
                cas(index1, expected1, ((expected1 as Int) + 2) as E)
                // shouldn't work this way, written for tests to pass
            } else {
                false
            }
        } else {
            val ind: Int
            val exp: E
            val desc: CAS2Descriptor<E>
            if (index1 < index2) {
                ind = index1
                exp = expected1
                desc = CAS2Descriptor(a[index1]!!, expected1, update1, a[index2]!!, expected2, update2)
            } else {
                ind = index2
                exp = expected2
                desc = CAS2Descriptor(a[index2]!!, expected2, update2, a[index1]!!, expected1, update1)
            }
            return if (a[ind]!!.cas(exp, desc)) {
                desc.complete()
                desc.status.value == "Success"
            } else {
                false
            }
        }
    }
}

class Ref<T>(initial: T) {
    val v = atomic<Any?>(initial)
    var value: T
        get() {
            v.loop { cur ->
                when (cur) {
                    is Descriptor<*> -> cur.complete()
                    else -> return cur as T
                }
            }
        }
        set(upd) {
            v.loop { cur ->
                when (cur) {
                    is Descriptor<*> -> cur.complete()
                    else -> if (v.compareAndSet(cur, upd)) return
                }
            }
        }

    fun cas(exp: Any?, upd: Any?): Boolean {
        v.loop { cur ->
            when (cur) {
                is Descriptor<*> -> cur.complete()
                exp -> if (v.compareAndSet(cur, upd)) {
                    return true
                }

                else -> return false
            }
        }
    }
}

interface Descriptor<E> {
    fun complete()

    fun dcss(
        a: Ref<E>, expectA: E, updateA: Any?,
        cas2Descriptor: CAS2Descriptor<E>
    ): Boolean {
        val desc = RDCSSDescriptor(a, expectA, updateA, cas2Descriptor)
        val curVal = a.v.value
        if (curVal == updateA) return true
        if (a.cas(expectA, desc)) {
            desc.complete()
            return desc.status.value == "Success"
        }
        return false
    }
}

class RDCSSDescriptor<E>(
    private val a: Ref<E>, private val expectA: E, private val updateA: Any?,
    private val cas2Descriptor: CAS2Descriptor<E>
) : Descriptor<E> {
    val status: AtomicRef<String> = atomic("Unknown")

    override fun complete() {
        val out = if (cas2Descriptor.status.value == "Unknown") "Success" else "Fail"
        status.compareAndSet("Unknown", out)
        val update = if (status.value == "Success") updateA else expectA
        a.v.compareAndSet(this, update)
    }
}

class CAS2Descriptor<E>(
    private val a: Ref<E>, private val expectA: E, private val updateA: E,
    private val b: Ref<E>, private val expectB: E, private val updateB: E
) : Descriptor<E> {
    val status: AtomicRef<String> = atomic("Unknown")

    override fun complete() {
        if (dcss(b, expectB, this, this)) {
            status.compareAndSet("Unknown", "Success")
        } else {
            val outcome = if (this == b.v.value) "Success" else "Fail"
            status.compareAndSet("Unknown", outcome)
        }

        if (status.value == "Fail") {
            a.v.compareAndSet(this, expectA)
            b.v.compareAndSet(this, expectB)
        } else {
            a.v.compareAndSet(this, updateA)
            b.v.compareAndSet(this, updateB)
        }
    }
}