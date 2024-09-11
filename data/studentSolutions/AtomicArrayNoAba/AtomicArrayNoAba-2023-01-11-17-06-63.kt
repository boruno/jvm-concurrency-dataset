import kotlinx.atomicfu.*
import java.sql.Ref

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<E>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) =
        a[index].value!!

    fun cas(index: Int, expected: E, update: E) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true
    }

    private abstract class Descriptor {
        abstract fun complete()
    }

    private class Ref<TValue>(initial: TValue) {
        val v = atomic<Any?>(initial)

        var value: TValue
            get() {
                v.loop { currentValue ->
                    when (currentValue) {
                        is Descriptor -> currentValue.complete()
                        else -> return currentValue as TValue
                    }
                }
            }
            set(update) {
                v.loop { currentValue ->
                    when (currentValue) {
                        is Descriptor -> currentValue.complete()
                        else -> if (v.compareAndSet(currentValue, update)) return
                    }
                }
            }
    }

    private class RDCSSDescriptor<AValue, BValue>(
        val a: Ref<AValue>, val expectA: AValue, val updateA: AValue,
        val b: Ref<BValue>, val expectB: BValue
    ) : Descriptor() {
        override fun complete() {
            val update = if (b.value === expectB) updateA else expectA
            a.v.compareAndSet(this, update)
        }
    }
}