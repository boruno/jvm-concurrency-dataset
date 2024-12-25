//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

//class TreiberStackWithElimination<E> {
//    private val top = atomic<Node<E>?>(null)
//    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
//
//    /**
//     * Adds the specified element [x] to the stack.
//     */
//    fun push(x: E) {
//        val elimIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
//        if (eliminationArray[elimIndex].value == null && eliminationArray[elimIndex].compareAndSet(null, x)) {
//            var i = 0
//            while (i != 10) {
//                i += 1
//                if (!eliminationArray[elimIndex].compareAndSet(x, x)) {
//                    return
//                }
//            }
//            if (!eliminationArray[elimIndex].compareAndSet(x, null)) {
//                return
//            }
//        }
//        while (true) {
//            val curTop = top.value
//            val newTop = Node(x, curTop)
//
//            if (top.compareAndSet(curTop, newTop)) {
//                return
//            }
//        }
//    }
//
//    /**
//     * Retrieves the first element from the stack
//     * and returns it; returns `null` if the stack
//     * is empty.
//     */
//    fun pop(): E? {
//        val elimIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
//        val value = eliminationArray[elimIndex].value
//        if (value != null) {
//            if (eliminationArray[elimIndex].compareAndSet(value, null)) {
//                return value as E
//            }
//        }
////        if (!eliminationArray[elimIndex].compareAndSet(null, null)) {
////            val value = eliminationArray[elimIndex].value
////            if (eliminationArray[elimIndex].compareAndSet(value, null)) {
////                return value as E
////            }
////        }
//        while (true) {
//            val curTop = top.value ?: return null
//            val newTop = curTop.next
//            if (top.compareAndSet(curTop, newTop)) {
//                return curTop.x
//            }
//        }
//    }
//}
//
//private class Node<E>(val x: E, val next: Node<E>?)
//
//private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val elimIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        if (eliminationArray[elimIndex].value == null && eliminationArray[elimIndex].compareAndSet(null, x)) {
            var i = 0
            while (i != 10) {
                i += 1
                if (!eliminationArray[elimIndex].compareAndSet(x, x)) {
                    return
                }
            }
            if (!eliminationArray[elimIndex].compareAndSet(x, null)) {
                return
            }
        }
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)

            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val elimIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        val value = eliminationArray[elimIndex].value
        if (value != null) {
            if (eliminationArray[elimIndex].compareAndSet(value, null)) {
                return value as E
            }
        }
        if (!eliminationArray[elimIndex].compareAndSet(null, null)) {
            val value = eliminationArray[elimIndex].value
            if (eliminationArray[elimIndex].compareAndSet(value, null)) {
                return value as E
            }
        }
        while (true) {
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT