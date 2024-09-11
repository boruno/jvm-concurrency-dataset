import kotlinx.atomicfu.*
import javax.management.Descriptor

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int): Int {

        var cur = a[index].value!!
        while (cur !is Int){
            completeCAS2(cur as DescriptorCAS2)
            cur = a[index].value!!
        }
        return cur
    }

    fun get_real(index: Int) = a[index].value!!

    fun cas(index: Int, expected: Any?, update: Any?) =
        a[index].compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: Int, update1: Int,
        index2: Int, expected2: Int, update2: Int
    ): Boolean {

        var a = get_real(index1)
        while (a is DescriptorCAS2){
            completeCAS2(a)
            a = get_real(index1)
        }
        val dc = DescriptorCAS2(
            index1, expected1, update1,
            index2, expected2, update2
        )
        if (cas(index1, expected1, dc)) {
            completeCAS2(dc)
        } else {
            dc.outcome.compareAndSet("Undecided", "Fail")
            cas(index1, dc, expected1)
        }

        return dc.outcome.value == "Success"

        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        /*if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true*/
    }

    fun completeCAS2(dc: DescriptorCAS2) {
        //dcss section
        if (dc.outcome.value == "Undecided") {
            if (cas(dc.index2, dc.expected2, dc)) {
                dc.outcome.compareAndSet("Undecided", "Success")
                cas(dc.index1, dc, dc.update1)
                cas(dc.index2, dc, dc.update2)
            } else {
                val real = get_real(dc.index2)
                if (real == dc) {
                    dc.outcome.compareAndSet("Undecided", "Success")
                    cas(dc.index1, dc, dc.update1)
                    cas(dc.index2, dc, dc.update2)
                } else if (real is DescriptorCAS2) {
                    completeCAS2(real)
                } else {
                    dc.outcome.compareAndSet("Undecided", "Fail")
                    cas(dc.index1, dc, dc.expected1)
                    cas(dc.index2, dc, dc.expected2)
                }
            }
        } else if (dc.outcome.value == "Success") {
            cas(dc.index1, dc, dc.update1)
            cas(dc.index2, dc, dc.update2)
        } else {
            cas(dc.index1, dc, dc.expected1)
            cas(dc.index2, dc, dc.expected2)
        }
    }

    class DescriptorCAS2(
        val index1: Int,
        val expected1: Any?,
        val update1: Any?,
        val index2: Int,
        val expected2: Any?,
        val update2: Any?
    ) {
        val outcome = atomic("Undecided")
    }
}