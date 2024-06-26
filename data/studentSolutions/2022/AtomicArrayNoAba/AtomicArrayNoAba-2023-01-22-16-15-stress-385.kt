import kotlinx.atomicfu.*

enum class ConsensusResult {
    FAIL,
    SUCCESS,
    UNDECIDED,
}

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = arrayOfNulls<Ref<E>>(size)

    init {
        for (i in 0 until size) a[i] = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index]!!.value

    fun cas(index: Int, expected: E, update: E) =
        a[index]!!.compareAndSet(expected, update)

    abstract class Descriptor {
        abstract fun complete()
    }

    class Ref<T>(initial: T) {
        val v = atomic<Any?>(initial)

        fun compareAndSet(expected: Any?, update: Any?): Boolean {
            while (true) {
                if (v.compareAndSet(expected, update)) {
                    return true
                }

                val tmp = v.value
                if (tmp !is Descriptor && tmp != expected) {
                    return false
                }
                (tmp as Descriptor).complete()
            }
        }

        var value: T
            set(upd) {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> if (v.compareAndSet(cur, upd)) return
                    }
                }
            }
            get() {
                v.loop { cur ->
                    when (cur) {
                        is Descriptor -> cur.complete()
                        else -> return cur as T
                    }
                }
            }
    }

    inner class RDCSSDescriptor<A, B>(
        val a: Ref<A>, val expectA: Any?, val updateA: Any?,
        val b: Ref<B>, val expectB: Any?, var outcome: Ref<ConsensusResult> = Ref(ConsensusResult.UNDECIDED)
    ) : Descriptor() {
        override fun complete() {
            val newConsensus = if (b.value == expectB) ConsensusResult.SUCCESS else ConsensusResult.FAIL
            outcome.v.compareAndSet(ConsensusResult.UNDECIDED, newConsensus)

            val upd = if (outcome.v.value == ConsensusResult.SUCCESS) updateA else expectA
            a.v.compareAndSet( this, upd)
        }
    }

    private inner class CASNDescriptor<A, B>(
        val a: Ref<A>, val expectA: A, val updateA: A,
        val b: Ref<B>, val expectB: B, val updateB: B,
        var outcome: Ref<ConsensusResult> = Ref(ConsensusResult.UNDECIDED)
    ) : Descriptor() {

        fun <A, B> dcss(
            a: Ref<A>, exceptA: Any?, updateA: Any?,
            b: Ref<B>, exceptB: Any?
        ): Boolean {
            val A = RDCSSDescriptor(
                a, exceptA, updateA,
                b, exceptB
            )
            if (!a.compareAndSet(exceptA, A)) {
                return false
            }
            A.complete()
            return A.outcome.value === ConsensusResult.SUCCESS
        }
        override fun complete() {
            val res = b.v.value == this || dcss(b, expectB, this, outcome, ConsensusResult.UNDECIDED)
            val update = if (res) ConsensusResult.SUCCESS else ConsensusResult.FAIL
            outcome.v.compareAndSet(ConsensusResult.UNDECIDED, update)
            val (A, B) = if (outcome.v.value == ConsensusResult.SUCCESS) updateA to updateB else expectA to expectB

            a.v.compareAndSet(this, A)
            b.v.compareAndSet(this, B)
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            return if (expected1 == expected2) cas(index1, expected1, (expected1 as Int + 2) as E) else false
        }
        val (t1, t2) = if (index1 > index2) {
            Triple(a[index1]!!, expected1, update1) to Triple(a[index2]!!, expected2, update2)
        } else {
            Triple(a[index2]!!, expected2, update2) to Triple(a[index1]!!, expected1, update1)
        }

        val (refA, expectedA, updateA) = t1
        val (refB, expectedB, updateB) = t2

        val A = CASNDescriptor(refA, expectedA, updateA, refB, expectedB, updateB)
        if (!refA.compareAndSet(expectedA, A)) return false

        A.complete()
        return A.outcome.value === ConsensusResult.SUCCESS
    }
}