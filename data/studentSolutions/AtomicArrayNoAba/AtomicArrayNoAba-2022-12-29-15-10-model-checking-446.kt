import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.loop

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref<E>>(size)
    private val d = atomic<Descriptor?>(null)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index].value!!.v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        val d = W2Descriptor(
            a[index1].value!!, expected1, update1,
            a[index2].value!!, expected2, update2
        )

        //if (index1 < index2) {
            if (!a[index1].value!!.v.compareAndSet(expected1, d)) {
                return false
            }

            d.complete()

            return d.result.value == true

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
        abstract fun complete()
    }

    class W2Descriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B, val updateB: B
    ) : Descriptor() {

        val result = atomic<Boolean?>(null)

        override fun complete() {
            if (result.value == true) {
                return
            }
//            if (a.v.compareAndSet(this, updateA)) {
//                b.v.compareAndSet(expectB, updateB)
//            } else {
//                a.v.compareAndSet(this, expectA)
//            }
            if (b.v.compareAndSet(expectB, updateB)) {
                a.v.compareAndSet(this, updateA)
                result.value = true
            } else {
                // aborting A
                a.v.compareAndSet(this, expectA)
                result.value = false
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
                    if (cur is Descriptor) cur.complete()
                    else return cur as T
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
    }

}