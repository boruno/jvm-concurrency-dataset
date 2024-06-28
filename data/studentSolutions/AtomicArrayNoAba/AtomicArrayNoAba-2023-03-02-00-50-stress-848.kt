import kotlinx.atomicfu.*

class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a[i].value = initialValue
    }

    fun get(index: Int) : Int {
        val cur = a[index].value!!

        if (cur is DescriptorCAS2) {
            if (index == cur.index2) {
                return cur.expected1 as Int
            } else {
                return cur.expected2 as Int
            }
        } else if (cur is DescriptorDCSS) {
            return cur.expected1 as Int
        }

        return cur as Int
    }

    fun get_real(index: Int) = a[index].value!!

    fun cas(index: Int, expected: Any?, update: Any?) =
        a[index].compareAndSet(expected, update)

    fun cas2(index1: Int, expected1: Int, update1: Int,
             index2: Int, expected2: Int, update2: Int): Boolean {

        val start = get_real(index1)
        if (start is DescriptorCAS2) {
            completeCAS2(start)
        }

        val dc = DescriptorCAS2(index1, expected1, update1,
            index2, expected2, update2)
        if (cas(index1, expected1, dc)) {
            completeCAS2(dc)
        }else {
            dc.outcome.value = "Fail"
        }

        return dc.outcome.value != "Fail"

        // TODO this implementation is not linearizable,
        // TODO a multi-word CAS algorithm should be used here.
        /*if (a[index1].value != expected1 || a[index2].value != expected2) return false
        a[index1].value = update1
        a[index2].value = update2
        return true*/
    }

    fun completeCAS2(dc: DescriptorCAS2) {
        val start = get_real(dc.index2)
        if (start is DescriptorCAS2) {
            completeCAS2_2(start)
        } else if (start is DescriptorDCSS) {
            completeDCSS(start)
            completeCAS2_2(start.update1 as DescriptorCAS2)
        }

        if (dcss(dc.index2, dc.expected2 as E, dc, dc.outcome.value, "Undecided")) {
            completeCAS2_2(dc)
        } else {
            dc.outcome.compareAndSet("Undecided", "Fail")
            cas(dc.index1, dc, dc.expected1)
        }


    }

    fun completeCAS2_2 (dc: DescriptorCAS2) {
        if (dc.outcome.value == "Success") {
            cas(dc.index1, dc, dc.update1)
            cas(dc.index2, dc, dc.update2)
        }
        if (dc.outcome.value == "Fail") {
            cas(dc.index1, dc, dc.expected1)
        }
        if (dc.outcome.value == "Undecided") {
            if (dc.outcome.compareAndSet("Undecided", "Success")) {
                cas(dc.index1, dc, dc.update1)
                cas(dc.index2, dc, dc.update2)
            } else {
                dc.outcome.compareAndSet("Undecided", "Fail")
                cas(dc.index1, dc, dc.expected1)
                cas(dc.index2, dc, dc.expected2)
            }
        }
    }
    fun dcss(index1: Int, expected1: E, update1: DescriptorCAS2,
             outcomeCAS: String, expected2: String): Boolean {

        val start = get_real(index1)

        if (start is DescriptorDCSS) {
            completeDCSS(start)
        }

        val dc = DescriptorDCSS(index1, expected1, update1, outcomeCAS, expected2)
        if (cas(index1, expected1, dc)) {
            completeDCSS(dc)
        } else {
            dc.outcome.value = "Fail"
        }

        return dc.outcome.value != "Fail"
    }

    fun completeDCSS(dc: DescriptorDCSS) {
        val real2 = dc.outcomeCAS
        if (real2 == dc.expected2) {
            if (dc.outcome.compareAndSet("Undecided", "Success")) {
                cas(dc.index1, dc, dc.update1)
            } else {
                val dcssRes = dc.outcome.value
                if (dcssRes == "Success") {
                    cas(dc.index1, dc, dc.update1)
                } else {
                    cas(dc.index1, dc, dc.expected1)
                }
            }
        } else {
            dc.outcome.compareAndSet("Undecided", "Fail")
            cas(dc.index1, dc, dc.expected1)
        }
    }

    class DescriptorDCSS(
        val index1: Int,
        val expected1: Any?,
        val update1: Any?,
        val outcomeCAS: String,
        val expected2: Any?
    ) {
        val outcome = atomic("Undecided")
    }

    class DescriptorCAS2 (
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