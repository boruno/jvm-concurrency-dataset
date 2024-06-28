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

        val d = W2Descriptor(
            a[index1].value!!, expected1, update1,
            a[index2].value!!, expected2, update2
        )

        if (!a[index1].value!!.cas(expected1, d)) {
            return false
        }


        d.complete()

        return d.consensus.get()!!

//        } else {
//            a[index2].value!!.v.compareAndSet(expected2, W2Descriptor(
//                a[index2].value!!, expected2, update2,
//                a[index1].value!!, expected1, update1
//            ))
//            return a[index2].value!!.value == update2 && a[index1].value!!.value == update1
//        }


        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
    }

    abstract class Descriptor {

        val consensus = AtomicReference<Boolean?>(null)
        abstract fun complete()
    }

    class RDCSSDescriptor<A>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val descriptor: Descriptor
    ) : Descriptor() {

        override fun complete() {
            val update = if (descriptor.consensus.get() == null)
                updateA else expectA
            consensus.compareAndSet(null, update == updateA)
            a.v.compareAndSet(this, update)
        }
    }


    class W2Descriptor<A : Any, B : Any>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B, val updateB: B
    ) : Descriptor() {

        override fun complete() {

            if (consensus.get() == null) {
                val r = b.dcss(expectB, this, this)
                consensus.compareAndSet(null, r)
            }


//            if (a.v.compareAndSet(this, updateA)) {
//                b.v.compareAndSet(expectB, updateB)
//                result.compareAndSet(null, true)
//                return true
//            } else {
//                a.v.compareAndSet(this, expectA)
//                result.compareAndSet(null, false)
//                return false
//            }


            if (consensus.get()!!) {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                // aborting A
                a.v.compareAndSet(this, expectA)
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
//                        if (cur.result.value!!) {
//                            return if (this === cur.a) cur.updateA as T else cur.updateB as T
//                        }

//                        else {
//                            return if (this === cur.b) cur.expectA as T else cur.expectB as T
//                        }
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

        fun cas(exp: Any?, upd: Any): Boolean {
            v.loop { cur ->
                if (cur is Descriptor) cur.complete()
                else {
                    return v.compareAndSet(exp, upd)
                }
            }
        }

        fun dcss(expected1: Any, update1: Any, descriptor: Descriptor) : Boolean {
            var d : RDCSSDescriptor<*>
            if (this.v.value is RDCSSDescriptor<*>) {
                d = this.v.value as RDCSSDescriptor<*>
            } else {
                d = RDCSSDescriptor(this as Ref<Any>, expected1, update1, descriptor)
                if (!v.compareAndSet(expected1, d)) {
                    return false
                }

            }
            d.complete()
            return d.consensus.get()!!
        }

    }

}