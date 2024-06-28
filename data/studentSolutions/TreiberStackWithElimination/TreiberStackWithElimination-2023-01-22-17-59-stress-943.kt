package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
//    fun push(x: E) {
//        val ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
//
//        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
//            if (i - ind < 5) {
//                break
//            }
//
//            val cur = eliminationArray[i]
//
//            var flag = false
//            for (j in 0 until 100) {
//                if (cur.compareAndSet(null, x)) {
////                    for (k in 0 until 1000) { }
//
//                    if (cur.compareAndSet(x, null)) {
//                        flag = true
//                        break
//                    } else {
//                        return
//                    }
//                }
//            }
//
//            if (flag) {
//                break
//            }
//        }
//
//        while (true) {
//            val curHead = top.value
//            val newHead = Node(x, curHead)
//            if (top.compareAndSet(curHead, newHead)) {
//                return
//            }
//        }
//    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
//    fun pop(): E? {
//        val ind = Random.nextInt(ELIMINATION_ARRAY_SIZE)
//
//        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
//            if (i - ind < 11) {
//                break
//            }
//
//            val cur = eliminationArray[i]
//
//            for (j in 0 until 10) {
//                val curVal = cur.value ?: continue
//
//                if (cur.compareAndSet(curVal, null)) {
//                    return curVal
//                }
//            }
//        }
//
//        while (true) {
//            val curHead = top.value ?: return null
//
//            if (top.compareAndSet(curHead, curHead.next)) {
//                return curHead.x
//            }
//        }
//    }

    private val r: ThreadLocalRandom? = ThreadLocalRandom.current()

    private fun pushIndices(index: Int, x: E): Boolean {
        if (eliminationArray[index].compareAndSet(null, x)
            || eliminationArray[index].compareAndSet(DONE as E, x)
        ) {
            for (i in 0 until WAITING_TIME) {
                if (eliminationArray[index].value == DONE) {
                    return true
                }
            }
            return !eliminationArray[index].compareAndSet(x, null)
        }
        return false
    }

    private fun noOptimizationPush(x: E) {
        while (true) {
            val curHead = top.value
            val newNode = Node(x, curHead)
            if (top.compareAndSet(curHead, newNode)) {
                return
            }
        }
    }

    fun push(x: E) {
        val index = r?.nextInt(ARRAY_SIZE) ?: 0
        if (pushIndices(index, x)) {
            return
        }
        if (index - 1 >= 0 && pushIndices(index - 1, x)) {
            return
        }
        if (index + 1 < ARRAY_SIZE && pushIndices(index + 1, x)) {
            return
        }
        noOptimizationPush(x)
    }

    private fun popIndices(index: Int): E? {
        val element = eliminationArray[index].value
        if (element != null && element != DONE) {
            if (eliminationArray[index].compareAndSet(element, DONE as E)) {
                return element
            }
        }
        return null
    }

    private fun noOptimizationPop(): E? {
        while (true) {
            val curHead = top.value
            if (curHead != null) {
                if (top.compareAndSet(curHead, curHead.next)) {
                    return curHead.x
                }
            } else {
                return null
            }
        }
    }

    fun pop(): E? {
        val index = r?.nextInt(ARRAY_SIZE) ?: 0
        var element = popIndices(index)
        if (element != null) {
            return element
        }
        if (index - 1 >= 0) {
            element = popIndices(index - 1)
            if (element != null) {
                return element
            }
        }
        if (index + 1 < ARRAY_SIZE) {
            element = popIndices(index + 1)
            if (element != null) {
                return element
            }
        }
        return noOptimizationPop()
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private val DONE = Any()

private const val ARRAY_SIZE = 10
private const val WAITING_TIME = 100