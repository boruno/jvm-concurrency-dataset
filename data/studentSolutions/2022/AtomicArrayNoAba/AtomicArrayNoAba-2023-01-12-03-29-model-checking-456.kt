//import kotlinx.atomicfu.*
//
//class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
//    private val a = atomicArrayOfNulls<Ref>(size)
//
//    init {
//        for (i in 0 until size) a[i].value = Ref(initialValue)
//    }
//
//    fun get(index: Int) =
//        a[index].value!!.get() as E
//
//
////    fun dcssMod(a: Ref, expectA: E, updateA: E, b: Ref, expectB: E) : Boolean {
////        if (a.get() == expectA && b.get() == expectB) {
////            a.set(updateA)
////            return true
////        } else {
////            return false
////        }
////    }
//
//    fun dcssMod(a: Ref, expectA: E, updateA: Any, b: Ref, expectB: Any): Boolean {
//        if (a.v.value == expectA && b.v.value == expectB) {
//            a.set(updateA)
//            return true
//        } else {
//            return false
//        }
//    }
//
//
//    fun cas(index: Int, expected: E, update: E): Boolean =
//        a[index].value!!.v.compareAndSet(expected, update)
//
//    fun cas2(
//        index1: Int, expected1: E, update1: E,
//        index2: Int, expected2: E, update2: E
//    ): Boolean {
//
//        val first: Ref
//        val second: Ref
//        val exp1: E
//        val exp2: E
//        val upd1: E
//        val upd2: E
//        if (index1 < index2) {
//            first = a[index1].value!!
//            second = a[index2].value!!
//            exp1 = expected1
//            exp2 = expected2
//            upd1 = update1
//            upd2 = update2
//        } else {
//            first = a[index2].value!!
//            second = a[index1].value!!
//            exp1 = expected2
//            exp2 = expected1
//            upd1 = update2
//            upd2 = update1
//        }
//
////        var secondVal = second.get()
////        var firstVal = first.get()
//
//        val descr = CASNDescriptor(first, exp1, upd1, second, exp2)
////        if (firstVal == exp1) {
////            if (!first.v.compareAndSet(exp1, descr)) {
////                return false
////            }
////        } else {
////            return false
////        }
//
//        if (!first.v.compareAndSet(exp1, descr)) {
//            return false
//        }
//        val descr2 = CASNDescriptor(second, exp2, upd2, first, exp1)
//        if (descr.outcome.value == "UNDECIDED") {
//            //if (dcssMod(second, exp2, upd2, first, exp1, secondVal , firstVal )) {
//
//            if (dcssMod(second, exp2, descr2, first, descr)) {
//                if (descr.outcome.compareAndSet("UNDECIDED", "SUCCESS")) {
////                    cas(index1, descr, update1)
////                    cas(index2, descr, update2)
//                    first.v.compareAndSet(descr, upd1)
//                    second.v.compareAndSet(descr2, upd2)
//                    return true
//                } else {
//                    //descr2.outcome.compareAndSet("UNDECIDED", descr.outcome.value)
//                    if (descr.outcome.value == "SUCCESS") {
//                        first.v.compareAndSet(descr, upd1)
//                        second.v.compareAndSet(descr2, upd2)
//                    } else {
//                        first.v.compareAndSet(descr, exp1)
//                        second.v.compareAndSet(descr2, exp2)
//                    }
//                    return descr.outcome.value == "SUCCESS"
//                }
//            } else {
//                descr.outcome.compareAndSet("UNDECIDED", "FAILED")
//                //cas(index1, descr, expected1)
//                first.v.compareAndSet(descr, exp1)
//                return false
//            }
//        }
////        firstVal = first.v.value
////        secondVal = second.v.value
////        if (firstVal is Descriptor) {
////            firstVal.complete()
////        }
////        if (secondVal is Descriptor) {
////            secondVal.complete()
////        }
//        if ( descr.outcome.value == "SUCCESS") {
//            first.v.compareAndSet(descr, upd1)
//            second.v.compareAndSet(descr2, upd2)
//        } else {
//            first.v.compareAndSet(descr, exp1)
//            second.v.compareAndSet(descr2, exp2)
//        }
//        return descr.outcome.value == "SUCCESS"
//
//    }
//}
//
//class Ref(initial: Any) {
//    val v = atomic<Any>(initial)
//
//    fun get(): Any {
//        while (true) {
//            val cur = v.value
//            if (cur is Descriptor) {
//                cur.complete()
//            } else {
//                return cur
//            }
//        }
//    }
//
//    fun set(upd: Any) {
//        while (true) {
//            val cur = v.value
//            if (cur is Descriptor) {
//                cur.complete()
//            } else {
//                if (v.compareAndSet(cur, upd)) {
//                    return
//                }
////                v.compareAndSet(cur, upd)
////                return
//            }
//        }
//    }
//}
//
//abstract class Descriptor {
//
//    abstract fun complete()
//}
//
//class CASNDescriptor(
//    val a: Ref, val expectA: Any, val updateA: Any, val b: Ref, val expectB: Any
//) : Descriptor() {
//
//    val outcome = atomic<String>("UNDECIDED")
//    override fun complete() {
//        val update: Any
//        val flag: Boolean
//
////        if (outcome.value == "SUCCESS") {
////            a.v.compareAndSet(this, updateA)
////        } else if (outcome.value == "FAILED") {
////            a.v.compareAndSet(this, expectA)
////        } else {
//
//            if (b.v.value == expectB) {
//                outcome.compareAndSet("UNDECIDED", "SUCCESS")
//                update = updateA
//                flag = true
//            } else {
//                outcome.compareAndSet("UNDECIDED", "FAILED")
//                update = expectA
//                flag = false
//            }
//            a.v.compareAndSet(this, update)
////            if (flag) {
////                outcome.compareAndSet("UNDECIDED", "SUCCESS")
////            } else {
////                outcome.compareAndSet("UNDECIDED", "FAILED")
////            }
//      //  }
//    }
//}









import kotlinx.atomicfu.*

class AtomicArrayNoAba<E : Any>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Ref>(size)

    init {
        for (i in 0 until size) a[i].value = Ref(initialValue)
    }

    fun get(index: Int) =
        a[index].value!!.get() as E


    fun dcssMod(a: Ref, expectA: E, updateA: Any, b: Ref, expectB: Any): Boolean {
        if (a.v.value == expectA && b.v.value == expectB) {
            a.set(updateA)
            return true
        } else {
            return false
        }
    }


    fun cas(index: Int, expected: E, update: E): Boolean =
        a[index].value!!.v.compareAndSet(expected, update)

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {

        if (index1 == index2 && expected1 != expected2) return false

        if (index1 == index2) {
            return cas(index1, expected1, ((expected1 as Int) + 2) as E)
        }

        val first: Ref
        val second: Ref
        val exp1: E
        val exp2: E
        val upd1: E
        val upd2: E
        if (index1 < index2) {
            first = a[index1].value!!
            second = a[index2].value!!
            exp1 = expected1
            exp2 = expected2
            upd1 = update1
            upd2 = update2
        } else {
            first = a[index2].value!!
            second = a[index1].value!!
            exp1 = expected2
            exp2 = expected1
            upd1 = update2
            upd2 = update1
        }

//        second.get()


        val descr = ADescriptor(first, exp1, upd1, second, exp2, upd2)

        while (true) {
            val cur = first.v.value
            if (cur is Descriptor) {
                cur.complete()
                continue
            }
            if (first.v.value == exp1) {
                if (first.v.compareAndSet(exp1, descr)) {
                    descr.complete()
                    return descr.outcome.value == "SUCCESS"
                }
            } else {
                return false
            }
        }
    }
}

class Ref(initial: Any) {
    val v = atomic<Any>(initial)

    fun get(): Any {
        while (true) {
            val cur = v.value
            if (cur is Descriptor) {
                cur.complete()
            } else {
                return cur
            }
        }
    }

    fun set(upd: Any) {
        while (true) {
            val cur = v.value
            if (cur is Descriptor) {
                cur.complete()
            } else {
                if (upd is ADescriptor) {
                    if (upd.outcome.value != "UNDECIDED") {
                        return
                    }
                }
                if (v.compareAndSet(cur, upd)) {
                    return
                }
            }
        }
    }
}

abstract class Descriptor {

    abstract fun complete()
}

class ADescriptor(
    val a: Ref, val expectA: Any, val updateA: Any, val b: Ref, val expectB: Any, val updateB: Any
) : Descriptor() {

    val outcome = atomic<String>("UNDECIDED")

    fun dcssMod(a: Ref, expectA: Any, updateA: Any, b: Ref, expectB: Any): Boolean {
        if (a.v.value == expectA && b.v.value == expectB) {
            a.set(updateA)
            return true
        } else {
            return false
        }
    }

    override fun complete() {
        while (true) {
            if (outcome.value == "UNDECIDED") {

                if (b.v.value == this) {
                    outcome.compareAndSet("UNDECIDED", "SUCCESS")
                    continue
                }

//                if (dcssMod(b, expectB, this, a, this)) {
//                    outcome.compareAndSet("UNDECIDED", "SUCCESS")
//                    continue
//                }
                val valu = b.v.value
                if (valu is ADescriptor) {
                    valu.complete()
                } else {
                    if (b.v.value == expectB) {
                        if (dcssMod(b, expectB, this, a, this)) {
                            outcome.compareAndSet("UNDECIDED", "SUCCESS")
                            continue
                        }
                    } else {
                        outcome.compareAndSet("UNDECIDED", "FAILED")
                        continue
                    }
                }


//                if (outcome.compareAndSet("UNDECIDED", "FAILED")) {
//                    continue
//                }

            } else if (outcome.value == "SUCCESS") {
                a.v.compareAndSet(this, updateA)
                b.v.compareAndSet(this, updateB)
                return
            } else if (outcome.value == "FAILED") {
                a.v.compareAndSet(this, expectA)
                b.v.compareAndSet(this, expectB)
                return
            }
        }
    }
}

//class BDescriptor(
//    val a: Ref, val expectA: Any, val updateA: Any, val b: Ref, val expectB: Any, val updateB: Any
//) : Descriptor() {
//
//    val outcome = atomic<String>("UNDECIDED")
//    override fun complete() {
//        val update: Any
//
//        if (b.get() == expectB) {
//            outcome.compareAndSet("UNDECIDED", "SUCCESS")
//            update = updateA
//        } else {
//            outcome.compareAndSet("UNDECIDED", "FAILED")
//            update = expectA
//        }
//        a.v.compareAndSet(this, update)
//    }
//}