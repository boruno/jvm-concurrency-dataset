import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index]!!.get()

    fun cas(index: Int, expected: E, update: E) =
        a[index]!!.cas(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        return when {
            index1 == index2 -> {
                expected1 == expected2 && update1 == update2 && cas(index1, expected1, update1)
            }
            index1 < index2 -> {
                cas2FromRightOrder(index1, expected1, update1, index2, expected2, update2)
            }
            else -> {
                cas2FromRightOrder(index2, expected2, update2, index1, expected1, update1)
            }
        }
    }

    private fun cas2FromRightOrder(indexA : Int, expectedA: E, updateA: E,
                                   indexB : Int, expectedB: E, updateB: E): Boolean {
        val refA = a[indexA]!!
        val refB = a[indexB]!!
        val descriptor = CAS2Descriptor(refA, expectedA, updateA, refB, expectedB, updateB)
        refA.v.compareAndSet(expectedA, descriptor)
        descriptor.complete()
        return descriptor.outcome.get()!!
    }
}

class Ref<E>(initial: E) {
    val v = atomic<Any?>(initial)

    @Suppress("UNCHECKED_CAST")
    fun get() : E {
        v.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return cur as E
            }
        }
    }

    fun set(upd : Any?) {
        v.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> if (v.compareAndSet(cur, upd))
                    return
            }
        }
    }

    fun cas(exp : Any?, upd : Any?) : Boolean {
        v.loop { cur ->
            when(cur) {
                is Descriptor -> cur.complete()
                else -> return v.compareAndSet(exp, upd)
            }
        }
    }
}

interface Descriptor {
    fun complete()
}

class DCSSDescriptor<A, B> (
    val a: Ref<A>, val expectA: A, val updateA: Any,
    val b: Ref<B>, val expectB: B
) : Descriptor
{
    val outcome : Ref<Boolean?> = Ref(null)

    override fun complete() {
        if (a.v.value != this) {
            outcome.v.compareAndSet(null, false)
        }
        if (b.v.value === expectB) {
            outcome.v.compareAndSet(null, true)
        } else {
            outcome.v.compareAndSet(null, false)
        }
        val update = if (outcome.get()!!) updateA else expectA
        a.v.compareAndSet(this, update)
    }
}

class CAS2Descriptor<A, B> (
    val a: Ref<A>, val expectA: A, val updateA: A,
    val b: Ref<B>, val expectB: B, val updateB: B
) : Descriptor
{
    val outcome : Ref<Boolean?> = Ref(null)

    override fun complete() {
        if (a.v.value != this) {
            outcome.v.compareAndSet(null, false)
        }
        if (dcss(b, expectB, this, outcome, null)) {
            outcome.v.compareAndSet(null, true)
        } else {
            outcome.v.compareAndSet(null, false)
        }
        val update = if (outcome.get()!!) updateA else expectA
        a.v.compareAndSet(this, update)
        b.v.compareAndSet(this, updateB)
    }
}

fun <A, B> dcss(ref1: Ref<A>, expected1: A, update1: Any,
                ref2: Ref<B>, expected2: B): Boolean {
    val descriptor = DCSSDescriptor(ref1, expected1, update1, ref2, expected2)
    ref1.v.compareAndSet(expected1, descriptor)
    descriptor.complete()
    return descriptor.outcome.get()!!
}
