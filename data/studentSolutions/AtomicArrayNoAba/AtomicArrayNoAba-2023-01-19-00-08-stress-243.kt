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
        val refA = Ref(a[index1].value)
        val refB = Ref(a[index2].value)
        val desc = RDCSSDescriptor(refA, expected1, update1,
            refB, expected2, update2)
        if (refA.v.compareAndSet(expected1, desc)){
            refA.value = update1
            return desc.outcome.value == "SUCCESS"
        }
        return false
    }

    abstract class Descriptor(){
        abstract fun complete()
    }

    class Ref<E>(initial: E){
        val v = atomic<Any?>(initial)

        var value: E
            get() {
                while (true){
                    val cur = v.value
                    when(cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as E
                    }
                }
            }
            set(upd) {
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

    class RDCSSDescriptor<E>(
        val a: Ref<E>, val expectA: E, val updateA: E,
        val b: Ref<E>, val expectB: E, val updateB: E
    ): Descriptor() {
        val outcome = atomic<String>("UNDECIDED")
        override fun complete() {
            val refOutcome = Ref(this.outcome.value) //DCSS for B starts here
            val desc2 = DCSSDescriptor<E, String>(b, expectB, this,
                refOutcome, "UNDECIDED")
            if (b.v.compareAndSet(expectB, desc2)){
                b.value = updateB //DCSS for B ends here
            }
            if (desc2.outcome.value == "SUCCESS"){
                this.outcome.compareAndSet("UNDECIDED", "SUCCESS")
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
            } else {
                this.outcome.compareAndSet("UNDECIDED", "FAIL")
                a.v.compareAndSet(this, expectA)
            }
        }
    }

    class DCSSDescriptor<E, T>(
        val a: Ref<E>, val expectA: E, val updateA: Any,
        val b: Ref<T>, val expectB: T): Descriptor() {
        val outcome = atomic<String>("UNDECIDED")
        override fun complete() {
            if (b.value === expectB){
                outcome.compareAndSet("UNDECIDED", "SUCCESS")
                a.v.compareAndSet(this, updateA)
            } else {
                outcome.compareAndSet("UNDECIDED", "FAIL")
                a.v.compareAndSet(this, expectA)
            }
        }
    }
}