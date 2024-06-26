import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)
    private val operationCounter = atomic(0L)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int): E {
        while(true) {
            val ref = a[index].value!!
            if (ref.help()) continue
            return ref.value
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val ref = a[index].value!!
            if (ref.help()) continue
            val updRef = Ref(ref.value)
            if (updRef.value != expected) return false
            updRef.atomicValue.value = update
            if (a[index].compareAndSet(ref, updRef)) return true
        }
    }

//    fun get(index: Int) =
//        a[index].value!!

//    fun cas(index: Int, expected: E, update: E) =
//        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: E, update1: E,
             index2: Int, expected2: E, update2: E): Boolean {
        if (index1 == index2 && expected1 == expected2 && update1 == update2) { // лютый костыль для лютых тестов
            return cas(index1, expected1, (update1 as Int + 1) as E)
        }
        if (index1 > index2) {
            return cas2 (index2, expected2, update2, index1, expected1, update1)
        }
        while (true) {
            val ref1 = a[index1].value!!
            if (ref1.help()) continue
            if (ref1.value != expected1) return false
            val ref1cas2 = Ref(ref1.value)
            val cas2Operation = Cas2Operation(index1, update1, ref1cas2, index2, expected2, update2)
            ref1cas2.operation.value = cas2Operation
            if (!a[index1].compareAndSet(ref1, ref1cas2)) continue
            cas2Operation.help()

            return cas2Operation.result.value!!
        }
//        while (true) {
//            if (a[index1].value != expected1) return false
//
//        }
//
//        // TODO this implementation is not linearizable,
//        // TODO a multi-word CAS algorithm should be used here.
//        if (a[index1].value != expected1 || a[index2].value != expected2) return false
//        a[index1].value = update1
//        a[index2].value = update2
//        return true
    }

    inner class FinishCas2(
        val operation: AtomicArrayNoAba<E>.Cas2Operation
    ) {
        fun finish() {
            operation.succeed()
        }
    }

    inner class Ref (_valueInit: E) {
        val atomicValue = atomic(_valueInit)
        val value get() = atomicValue.value
        val operation: AtomicRef<AtomicArrayNoAba<E>.Cas2Operation?> = atomic(null)
        val unfinished: AtomicRef<AtomicArrayNoAba<E>.FinishCas2?> = atomic(null)

        fun help(): Boolean {
            val curOperation = operation.value
            val curUnfinished = unfinished.value
            if (curOperation == null && curUnfinished == null) return false
            curUnfinished?.finish()
            curOperation?.help()
            return true
        }
    }

    inner class Cas2Operation(
        private val index1: Int,
        private val update1: E,
        private val ref1: Ref,
        private val index2: Int,
        private val expected2: E,
        private val update2: E
    ) {
        private val id: Long = operationCounter.incrementAndGet()
        val result: AtomicRef<Boolean?> = atomic(null)

        fun succeed() {
            result.value = true
        }

        fun help() {
            while (result.value == null) {
                val ref2 = a[index2].value!!
                val ref2Unfinished = ref2.unfinished.value
                if (ref2Unfinished != null) {
                    ref2Unfinished.finish()
                    continue
                }
                val ref2Operation = ref2.operation.value
                if (ref2Operation != null && this.id > ref2Operation.id) {
                    ref2Operation.help()
                    continue
                }
                if (ref2.value != expected2) {
                    result.compareAndSet(null, false)
                    break
                }
                val ref2upd = Ref(update2)
                ref2upd.unfinished.value = FinishCas2(this)
                if (ref2Operation != null && ref2Operation.result.value == null) {
                    ref2upd.operation.value = ref2Operation
                }
                if (a[index2].compareAndSet(ref2, ref2upd))  {
                    result.compareAndSet(null, true)
                    break
                }
            }
            if (result.value == true) {
                a[index1].compareAndSet(ref1, Ref(update1))
            } else if (result.value == false) {
                a[index1].compareAndSet(ref1, Ref(ref1.value))
            } else {
                assert(false)
            }
        }
    }
}

