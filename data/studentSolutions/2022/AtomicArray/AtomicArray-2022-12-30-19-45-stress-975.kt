import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicReference

class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)


    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.cas(expected, update as Any)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        if (index1 == index2) {
            if (expected1 != expected2) {
                return false
            }
            return cas(index1, expected1, update2)
        }

        if (index1 < index2) {
            val d = W2Descriptor(
                a[index1].value!!, expected1, update1,
                a[index2].value!!, expected2, update2
            )

            if (!a[index1].value!!.cas(expected1, d)) {
                return false
            }

            d.complete()

            return d.consensus.get()!!
        } else {
            val d = W2Descriptor(
                a[index2].value!!, expected2, update2,
                a[index1].value!!, expected1, update1,
            )

            if (!a[index2].value!!.cas(expected2, d)) {
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

            val d = b.dcss(expectB, this, this)
            if (d != null) {
                d.complete()
            }

            consensus.compareAndSet(null, b.v.value == this)

            assert(consensus.get() != null)

            if (consensus.get()!!) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                // aborting A
                a.v.compareAndSet(this, expectA)
                //b.v.compareAndSet(this, expectB)
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

        fun cas(exp: T, upd: Any): Boolean {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else {
                    if (cur == exp) {
                        val r = v.compareAndSet(exp, upd)
                        if (r) {
                            return true
                        }
                    } else {
                        return false
                    }
                }
            }
        }

        fun dcss(expected: T, update: W2Descriptor<T>, consensusDescriptor: Descriptor) : RDCSSDescriptor<T>? {
            v.loop { cur ->
                if (cur is W2Descriptor<*>) {
                    if (consensusDescriptor !== cur ) {
                        cur.complete()
                    } else {
                        return null
                    }
                }
                else if (cur is RDCSSDescriptor<*>) {
                    return cur as RDCSSDescriptor<T>
                } else {
                    val d = RDCSSDescriptor(this, expected, update, consensusDescriptor)
                    if (cur != expected) {
                        return null
                    }
                    if (v.compareAndSet(expected, d)) {
                        return d
                    }
                }
            }
        }

    }

}