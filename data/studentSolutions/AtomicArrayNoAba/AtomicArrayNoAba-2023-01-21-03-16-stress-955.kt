import kotlinx.atomicfu.*

class AtomicArrayNoAba(size: Int, initialValue: Int) {
    private val a = atomicArrayOfNulls<Element<Int>>(size)

    init {
        for (i in 0 until size) {
            a[i].value = Element(initialValue)
        }
    }

    fun get(index: Int): Int {
        while (true) {
            val indexElement = a[index].value!!.element.value
            if (indexElement.second != null) {
                helpCAS2(indexElement.second!!)
                continue
            }
            return indexElement.first
        }
    }

    fun cas(index: Int, expected: Int, update: Int): Boolean {
        while (true) {
            val curElement = a[index].value!!.element.value

            if (curElement.second != null) {
                helpCAS2(curElement.second!!)
                continue
            }

            if (curElement.first == expected) {
                return a[index].value!!.element.compareAndSet(curElement, Pair(update, null))
            }
            else {
                return false
            }
        }
    }

    fun cas2(index1: Int, expected1: Int, update1: Int,
             index2: Int, expected2: Int, update2: Int): Boolean {

        val thisCASNDescriptor = CASNDescriptor(index1, expected1, update1, index2, expected2, update2)

        if (index1 == index2 && expected1 == expected2 && update1 == update2) {
            return cas(index1, expected1, update1 + 1)
        }

        while (true) {
            helpCAS2(thisCASNDescriptor)

            if (thisCASNDescriptor.outcome.value == Outcome.SUCCESS) {
                return true
            }
            if (thisCASNDescriptor.outcome.value == Outcome.FAIL) {
                return false
            }
        }
    }

    fun helpCAS2(casnDescriptor: CASNDescriptor<Int>) {
        while (true) {
            if (casnDescriptor.outcome.value != Outcome.UNDECIDED) {
                helpCAS2Ending(casnDescriptor)
                return
            }

            val index1Element = a[casnDescriptor.index1].value!!.element.value
            val index2Element = a[casnDescriptor.index2].value!!.element.value

            if (index1Element.second != null && index2Element.second != null) {
                if (index1Element.second == casnDescriptor && index2Element.second != casnDescriptor) {
                    if (conflictingCAS2(casnDescriptor, index2Element.second!!)) {
                        if (casnDescriptor.outcome.value == Outcome.UNDECIDED && index2Element.second!!.outcome.value == Outcome.UNDECIDED) {
                            if (index1Element.first == index2Element.second!!.expect2) {
                                if (a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(index1Element.first, index2Element.second))) {
                                    index2Element.second!!.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                                    helpCAS2Ending(index2Element.second)
                                    continue
                                }
                            }
                            else {
                                if (a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(index1Element.first, null))) {
                                    index2Element.second!!.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                                    helpCAS2Ending(index2Element.second)
                                    continue
                                }
                            }
                        }
                    }
                }
            }

            if (index1Element.second != casnDescriptor && index1Element.second != null) { // installed another descriptor
                helpCAS2WithoutRecursion(index1Element.second!!)
                continue
            }

            if (index2Element.second != casnDescriptor && index2Element.second != null) { // installed another descriptor
                helpCAS2WithoutRecursion(index2Element.second!!)
                continue
            }

            if (casnDescriptor.outcome.value == Outcome.UNDECIDED) { // here both indexes have either casnDescriptor or null installed
                undecidedCAS2(casnDescriptor, index1Element, index2Element)
            }
        }
    }

    fun undecidedCAS2(casnDescriptor: CASNDescriptor<Int>, index1Element: Pair<Int, CASNDescriptor<Int>?>, index2Element: Pair<Int, CASNDescriptor<Int>?>) {
        if (index1Element.second == null) {
            if (index1Element.first == casnDescriptor.expect1) {
                if (a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(index1Element.first, casnDescriptor))) {
                    if (index2Element.second == null) {
                        if (index2Element.first == casnDescriptor.expect2) {
                            if (a[casnDescriptor.index2].value!!.element.compareAndSet(index2Element, Pair(index2Element.first, casnDescriptor))) {
                                casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                            }
                        }
                        else {
                            casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                        }
                    }
                    else {
                        casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                    }
                }
            }
            else {
                casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
            }
        }
        else {
            if (index2Element.second == null) {
                if (index2Element.first == casnDescriptor.expect2) {
                    if (a[casnDescriptor.index2].value!!.element.compareAndSet(index2Element, Pair(index2Element.first, casnDescriptor))) {
                        casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
                    }
                }
                else {
                    casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
                }
            }
            else {
                casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
            }
        }
    }

    fun helpCAS2WithoutRecursion(casnDescriptor: CASNDescriptor<Int>) {
        if (casnDescriptor.outcome.value != Outcome.UNDECIDED) {
            helpCAS2Ending(casnDescriptor)
            return
        }

        val index1Element = a[casnDescriptor.index1].value!!.element.value
        val index2Element = a[casnDescriptor.index2].value!!.element.value

        if (index1Element.second != casnDescriptor && index1Element.second != null) { // installed another descriptor
            return
        }

        if (index2Element.second != casnDescriptor && index2Element.second != null) { // installed another descriptor
            return
        }

        if (casnDescriptor.outcome.value == Outcome.UNDECIDED) { // here both indexes have either casnDescriptor or null installed
            undecidedCAS2(casnDescriptor, index1Element, index2Element)
        }
        helpCAS2Ending(casnDescriptor)
        return
    }

    fun helpCAS2Ending(casnDescriptor: CASNDescriptor<Int>?) {
        if (casnDescriptor != null) {
            val index1Element = a[casnDescriptor.index1].value!!.element.value
            val index2Element = a[casnDescriptor.index2].value!!.element.value

            if (casnDescriptor.outcome.value == Outcome.FAIL) {
                if (index1Element.second == casnDescriptor) {
                    a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(index1Element.first, null))
                }
                if (index2Element.second == casnDescriptor) {
                    a[casnDescriptor.index2].value!!.element.compareAndSet(index2Element, Pair(index2Element.first, null))
                }
            }
            if (casnDescriptor.outcome.value == Outcome.SUCCESS) {
                if (index1Element.second == casnDescriptor) {
                    a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(casnDescriptor.update1, null))
                }
                if (index2Element.second == casnDescriptor) {
                    a[casnDescriptor.index2].value!!.element.compareAndSet(index2Element, Pair(casnDescriptor.update2, null))
                }
            }
            return
        }
    }

    fun conflictingCAS2(casnDescriptor1: CASNDescriptor<Int>, casnDescriptor2: CASNDescriptor<Int>): Boolean {
        return casnDescriptor1.index1 == casnDescriptor2.index2 && casnDescriptor1.index2 == casnDescriptor2.index1
    }

    enum class Outcome {
        UNDECIDED,
        SUCCESS,
        FAIL
    }

    class CASNDescriptor<E>(val index1: Int, val expect1: E, val update1: E,
                            val index2: Int, val expect2: E, val update2: E
    ){
        val outcome = atomic(Outcome.UNDECIDED)
    }

    class Element<E>(e: E){
        val element = atomic(Pair<E, CASNDescriptor<E>?>(e, null))
    }
}

//class AtomicArrayNoAba<E>(size: Int, initialValue: E) {
//    private val a = atomicArrayOfNulls<Element<E>>(size)
//
//    init {
//        for (i in 0 until size) {
//            a[i].value = Element(initialValue)
//        }
//    }
//
//    fun get(index: Int): E {
//        while (true) {
//            val indexElement = a[index].value!!.element.value
//            if (indexElement.second != null) {
//                helpCAS2(index)
//                continue
//            }
//            return indexElement.first
//        }
//    }
//
//    fun cas(index: Int, expected: E, update: E): Boolean {
//        while (true) {
//            val curElement = a[index].value!!.element.value
//
//            if (curElement.second == null) {
//                if (curElement.first == expected) {
//                    return a[index].value!!.element.compareAndSet(curElement, Pair(update, null))
//                }
//                else {
//                    return false
//                }
//            }
//            else {
//                helpCAS2(index)
//            }
//        }
//    }
//
//    fun cas2(index1: Int, expected1: E, update1: E,
//             index2: Int, expected2: E, update2: E): Boolean {
//
//        val thisCASNDescriptor = CASNDescriptor(index1, expected1, update1, index2, expected2, update2)
//
//        if (index1 == index2) {
//            if (expected1 == expected2 && update1 == update2) {
//                return cas(index1, expected1, update1 + 1)
//            }
//
//            if (cas(index1, expected1, update1)) {
//                return true
//            }
//            return cas(index2, expected2, update2)
//        }
//
//        while (true) {
//            val index1Element = a[index1].value!!.element.value
//
//            if (index1Element.second == null) {
//                if (index1Element.first == expected1) {
//                    if (a[index1].value!!.element.compareAndSet(index1Element, Pair(index1Element.first, thisCASNDescriptor))) {
//                        helpCAS2(index1)
//                        if (thisCASNDescriptor.outcome.value == Outcome.SUCCESS) {
//                            return true
//                        }
//                        if (thisCASNDescriptor.outcome.value == Outcome.FAIL) {
//                            return false
//                        }
//                    }
//                }
//                else {
//                    thisCASNDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
//                    if (thisCASNDescriptor.outcome.value == Outcome.FAIL) {
//                        return false
//                    }
//                }
//            }
//            else {
//                helpCAS2(index1)
//            }
//        }
//    }
//
//    fun helpCAS2(index: Int) { // index is either 1 or 2
//        val indexElement = a[index].value!!.element.value
//        val casnDescriptor = indexElement.second
//
//        if (casnDescriptor != null) {
//            while (true) {
//                val index1Element = a[casnDescriptor.index1].value!!.element.value
//                val index2Element = a[casnDescriptor.index2].value!!.element.value
//
//                if (casnDescriptor.outcome.value == Outcome.UNDECIDED) {
//                    val what = 1
//                    if (index1Element.second == null) {
//                        if (index1Element.first == casnDescriptor.expect1) {
//                            if (casnDescriptor.outcome.value == Outcome.UNDECIDED) { // intentionally redundant, if first element desc. is null, the operation must be either success or fail
//                                a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(index1Element.first, casnDescriptor))
//                            }
//                        }
//                        else {
//                            casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL) // intentionally redundant
//                        }
//                    }
//                    else {
//                        if (index1Element.second != casnDescriptor) {
//                            helpCAS2(casnDescriptor.index1)
//                        }
//                    }
//
//                    if (index2Element.second == null) {
//                        if (index2Element.first == casnDescriptor.expect2) {
//                            if (casnDescriptor.outcome.value == Outcome.UNDECIDED) {
//                                if (a[casnDescriptor.index2].value!!.element.compareAndSet(index2Element, Pair(index2Element.first, casnDescriptor))) {
//                                    casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
//                                }
//                            }
//                        }
//                        else {
//                            casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.FAIL)
//                        }
//                    }
//                    else {
//                        if (index2Element.second != casnDescriptor) {
//                            helpCAS2(casnDescriptor.index2)
//                        }
//                        else {
//                            casnDescriptor.outcome.compareAndSet(Outcome.UNDECIDED, Outcome.SUCCESS)
//                        }
//                    }
//                }
//                else {
//                    if (casnDescriptor.outcome.value == Outcome.FAIL) {
//                        if (index1Element.second == casnDescriptor) {
//                            a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(index1Element.first, null))
//                        }
//                        if (index2Element.second == casnDescriptor) { // intentionally redundant
//                            a[casnDescriptor.index2].value!!.element.compareAndSet(index2Element, Pair(index2Element.first, null))
//                        }
//                    }
//                    if (casnDescriptor.outcome.value == Outcome.SUCCESS) {
//                        if (index1Element.second == casnDescriptor) {
//                            a[casnDescriptor.index1].value!!.element.compareAndSet(index1Element, Pair(casnDescriptor.update1, null))
//                        }
//                        if (index2Element.second == casnDescriptor) {
//                            a[casnDescriptor.index2].value!!.element.compareAndSet(index2Element, Pair(casnDescriptor.update2, null))
//                        }
//                    }
//                    return
//                }
//            }
//        }
//    }
//
//    enum class Outcome {
//        UNDECIDED,
//        SUCCESS,
//        FAIL
//    }
//
//    class CASNDescriptor<E>(val index1: Int, val expect1: E, val update1: E,
//                            val index2: Int, val expect2: E, val update2: E
//    ){
//        val outcome = atomic(Outcome.UNDECIDED)
//    }
//
//    class Element<E>(e: E){
//        val element = atomic(Pair<E, CASNDescriptor<E>?>(e, null))
//    }
//}
