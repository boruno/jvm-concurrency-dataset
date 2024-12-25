//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<Long, E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
//        val threadInfo = ThreadInfo(Thread.currentThread().id, x, StackOperation.PUSH)
//        if (!tryToPerformStackOperation(threadInfo)) {
//
//        }

        (0..2).random()
        val element = Pair(Thread.currentThread().id, x)
        for (i in (0 until ELIMINATION_ARRAY_SIZE)) {
            if (eliminationArray[i].compareAndSet(null, element)) {
//                Thread.sleep(10)
                if (!eliminationArray[i].compareAndSet(element, null)) {
                    return
                }
                break
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
//        val threadInfo = ThreadInfo<E>(Thread.currentThread().id, null, StackOperation.POP)
//        if (!tryToPerformStackOperation(threadInfo)) {
//
//        }
//        return threadInfo.x

        for (i in (0 until ELIMINATION_ARRAY_SIZE)) {
            val element = eliminationArray[i].value ?: continue
            if (eliminationArray[i].compareAndSet(element, null)) {
                return element.second
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

    private fun lesOp(threadInfo: ThreadInfo<E>) {
        while (true) {

        }
    }

    private fun tryToPerformStackOperation(threadInfo: ThreadInfo<E>): Boolean {
        if (threadInfo.op == StackOperation.PUSH) {
            val curTop = top.value
            val newTop = Node(threadInfo.x!!, curTop)
            return top.compareAndSet(curTop, newTop)
        } else if (threadInfo.op == StackOperation.POP) {
            val curTop = top.value
            if (curTop == null) {
                threadInfo.x = null
                return true
            }
            val newTop = curTop.next
            return if (top.compareAndSet(curTop, newTop)) {
                threadInfo.x = curTop.x
                true
            } else {
                threadInfo.x = null
                false
            }
        } else {
            throw IllegalArgumentException("Unrecognized operation: " + threadInfo.op)
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private enum class StackOperation {
    PUSH, POP
}

private class ThreadInfo<E>(val id: Long, var x: E?, val op: StackOperation)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT