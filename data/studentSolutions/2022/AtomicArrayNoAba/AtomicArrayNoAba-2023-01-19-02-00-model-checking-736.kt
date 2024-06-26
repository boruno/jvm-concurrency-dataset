import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.

        if (a[index1].value == null || a[index2].value == null){
            throw IllegalArgumentException()
        }
        val refA = Ref<E>(index1)
        val refB = Ref<E>(index2)
        val desc = RDCSSDescriptor(refA, expected1, update1,
            refB, expected2, update2)
        val curD = refA.ds.value
        if (curD != null){
            if (curD is Descriptor){
                curD.complete()
            }
        }
        if (refA.ds.compareAndSet(null, desc)){
//            refA.value = update1
            desc.complete()
            return desc.outcome.value == "SUCCESS"
        }
        return false
    }

    abstract class Descriptor(){
        abstract fun complete()
    }

    inner class Ref<T>(initial: Int){
        val index = initial
//        val v = a[initial].value
        val ds: AtomicRef<Any?> = atomic(null)

        var value: T
            get() {
                while (true){
                    val ind = index
                    val cur = a[ind]
                    val curD = ds.value
                    if (curD is Descriptor){
                        curD.complete()
                        continue
                    }
                    return cur.value as T
                }
            }
            set(value) {}
//            set(upd: E) {
//                while (true){
//                    val cur = v.value
//                    when(cur) {
//                        is Descriptor -> cur.complete()
//                        else -> if (v.compareAndSet(cur, upd))
//                            return
//                    }
//                }
//            }
    }

    inner class Ref2<T>(initial: T){
        val v = atomic<Any?>(initial)

        var value: T
            get() {
                while (true){
                    val cur = v.value
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
            set(upd: T) {
                while (true){
                    val cur = v.value
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd))
                            return
                    }
                }
            }
    }

    inner class RDCSSDescriptor(
        val ra: Ref<E>, val expectA: E, val updateA: E,
        val rb: Ref<E>, val expectB: E, val updateB: E
    ): Descriptor() {
        val outcome = atomic<String>("UNDECIDED")
        override fun complete() {
            val refOutcome = Ref2(this.outcome.value) //DCSS for B starts here
            val desc2 = DCSSDescriptor<String>(rb, expectB, this,
                refOutcome, "UNDECIDED")
            if (rb.ds.compareAndSet(null, desc2)){
                val curD = rb.ds.value//DCSS for B ends here
                if (curD != null){
                    if (curD is Descriptor){
                        curD.complete()
                    }
                }
                desc2.complete()
            }
            if (desc2.outcome.value == "SUCCESS"){
                this.outcome.compareAndSet("UNDECIDED", "SUCCESS")
                ra.ds.compareAndSet(this, null)
                val ind1 = ra.index
                a[ind1].compareAndSet(expectA, updateA)
                rb.ds.compareAndSet(this, null)
                val ind2 = rb.index
                a[ind2].compareAndSet(expectB, updateB)
            } else {
                this.outcome.compareAndSet("UNDECIDED", "FAIL")
                ra.ds.compareAndSet(this, null)
                a[ra.index].compareAndSet(expectA, updateA)
            }
        }
    }

    inner class DCSSDescriptor<G>(
        val a: Ref<E>, val expectA: E, val updateA: Any,
        val b: Ref2<G>, val expectB: G): Descriptor() {
        val outcome = atomic<String>("UNDECIDED")
        override fun complete() {
            if (b.value === expectB){
                outcome.compareAndSet("UNDECIDED", "SUCCESS")
                a.ds.compareAndSet(this, updateA)
            } else {
                outcome.compareAndSet("UNDECIDED", "FAIL")
                a.ds.compareAndSet(this, updateA)
            }
        }
    }
}

fun main(){
    val g = AtomicArrayNoAba<Int>(10, 0)
    g.cas2(2, 0, 1, 0, 0, 1)
    println(g.get(0))
}