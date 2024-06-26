import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)


    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        if (index1 == index2) {
            return cas(index1, expected1, update1)
        }

        if (index1 < index2) {
            val d = W2Descriptor(
                a[index1].value!!, expected1, update1,
                a[index2].value!!, expected2, update2
            )

            if (!a[index1].value!!.casDescriptor(expected1, d)) {
                return false
            }

            d.complete()

            return d.consensus.get()!!
        } else {
            val d = W2Descriptor(
                a[index2].value!!, expected2, update2,
                a[index1].value!!, expected1, update1,
            )

            if (!a[index2].value!!.casDescriptor(expected2, d)) {
                return false
            }

            d.complete()

            return d.consensus.get()!!
        }

    }

    abstract class Descriptor {

        val consensus = AtomicReference<Boolean?>(null)
        abstract fun complete()
    }

    class RDCSSDescriptor<E>(
        val a: Ref<E>, val expectA: E, val updateA: W2Descriptor<E>,
        val consensusDescriptor: Descriptor
    ) : Descriptor() {

        override fun complete() {
            val c = consensusDescriptor.consensus.get() == null
            consensus.compareAndSet(null, c)
            if (consensus.get()!!) {
                a.v.compareAndSet(this, updateA)
            } else {
                a.v.compareAndSet(this, expectA)
            }
        }
    }


    class W2Descriptor<E>(
        val a: Ref<E>, val expectA: E, val updateA: E,
        val b: Ref<E>, val expectB: E, val updateB: E
    ) : Descriptor() {

        override fun complete() {

            if (consensus.get() == null && b.v.value != this) {
                val r = b.dcss(expectB, this, this)
                if (b.v.value != this) {
                    consensus.compareAndSet(null, r)
                } else {
                    consensus.compareAndSet(null, true)
                }

            }

            if (b.v.value == this && consensus.get() == null) {
                consensus.compareAndSet(null, true)
            }

            if (consensus.get()!!) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                // aborting A
                a.v.compareAndSet(this, expectA)
                a.v.compareAndSet(this, expectB)
            }

        }

    }

    /**
     * Reference to a Descriptor / T-value
     *
     * complete() finishes current cas-2 operation
     */
    class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                v.loop { cur ->
                    if (cur is Descriptor) {
                        cur.complete()
                    } else {
                        return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    if (cur is Descriptor) cur.complete()
                    else {
                        if (v.compareAndSet(cur, upd)) {
                            return
                        }
                    }
                }
            }

        fun cas(exp: T, upd: T): Boolean {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else {
                    return v.compareAndSet(exp, upd)
                }
            }
        }

        fun casDescriptor(exp: T, upd: Descriptor) : Boolean {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else {
                    return v.compareAndSet(exp, upd)
                }
            }
        }

        fun dcss(expected: T, update: W2Descriptor<T>, consensusDescriptor: Descriptor) : Boolean {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else {
                    val d = RDCSSDescriptor(this, expected, update, consensusDescriptor)
                    if (!v.compareAndSet(expected, d) /* && !(v.value is RDCSSDescriptor<*>) && d.consensus.get() == null */ ) {
                        return false
                    }
                    d.complete()
                    return d.consensus.get()!!
                }
            }
        }

    }

}