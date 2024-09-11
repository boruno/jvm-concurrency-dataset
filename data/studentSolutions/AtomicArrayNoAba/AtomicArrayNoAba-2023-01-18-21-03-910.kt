import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a: Array<Ref<E>> = Array(size) { Ref(initialValue) }

    class DescElem<E>(val currentRef: Ref<E>, val expect: E, val update: E, val currIndex: Int)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    // From lecture
    class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                v.loop { cur ->
                    when (cur) {
                        is StrangeDescriptor<*> -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is StrangeDescriptor<*> -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }
    }

    // From lecture
    class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B
    ) {
        fun complete() {
            val update = if (b.value === expectB)
                updateA else expectA
            a.v.compareAndSet(this, update)
        }
    }

    enum class SomeStatus {
        UND, FAIL, SUCC
    }

    class StrangeDescriptor<T>(val elemA: DescElem<T>,
                               val elemB: DescElem<T>,) {
        val oc = atomic(SomeStatus.UND)

        fun complete(): Boolean {
//            val update = if (b.value === expectB)
//                updateA else expectA
//            a.v.compareAndSet(this, update)

            while (true) {
                val elemBRef = elemB.currentRef.v
                if (elemBRef.compareAndSet(elemB.expect, this)) {
                    oc.compareAndSet(SomeStatus.UND, SomeStatus.SUCC)
                    break
                } else {
                    val elemBVal = elemB.currentRef.v.value
                    if (elemBVal != elemB.expect && elemBVal !is StrangeDescriptor<*>) {
                        oc.compareAndSet(SomeStatus.UND, SomeStatus.FAIL)
                        break
                    } else if (elemBVal is StrangeDescriptor<*>) {
                        if (elemBVal != this) {
                            elemBVal.complete()
                        } else {
                            oc.compareAndSet(SomeStatus.UND, SomeStatus.SUCC)
                            break
                        }
                    }
                }
            }



            if (oc.value == SomeStatus.SUCC) {
                // all is good
                elemA.currentRef.v.compareAndSet(this, elemA.update)
                elemB.currentRef.v.compareAndSet(this, elemB.update)
                return true
            } else if (oc.value == SomeStatus.FAIL) {
                // rollback to previous result, because now is false
                elemA.currentRef.v.compareAndSet(this, elemA.expect)
                return false
            }
            return false
        }
    }


    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: Int) =
        a[index].v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 != expected2) return false
            return cas(index1, expected1, update2 as Int + 1) // bad test | get(2): 1 [-,1] | inc(2, 2): void |
        }
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false

//
//        a[index1].value = update1
//        a[index2].value = update2
//        return true

        val first = DescElem<E>(a[index1], expected1, update1, index1)
        val second = DescElem<E>(a[index2], expected2, update2, index2)

        while (true) {
            val currDescr = StrangeDescriptor(first, second)
            if (first.currentRef.v.compareAndSet(first.expect, currDescr)) {
                if (currDescr.complete()) return true
                return false
            } else {
                return false
            }
        }
    }
}