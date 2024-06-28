package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import mpp.stackWithElimination.State.*
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Element<E>?>(ELIMINATION_ARRAY_SIZE)
    private val random = ThreadLocalRandom.current()

    private val doneElement = Element(DONE, null as E?)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val ind = random.nextInt(ELIMINATION_ARRAY_SIZE)
        val element = Element(VALUE, x)
        while (true) {
            if (eliminationArray[ind].compareAndSet(null, element)) {
                break
            }
        }
        for (i in 0 until PUSH_WAIT_ITERATIONS_COUNT) {
            if (eliminationArray[ind].compareAndSet(doneElement, null)) {
                return
            }
        }
        eliminationArray[ind].compareAndSet(element, null)
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
//        val ind = random.nextInt(ELIMINATION_ARRAY_SIZE)
//        val element = eliminationArray[ind].value
//        if (element?.state == VALUE && eliminationArray[ind].compareAndSet(element, doneElement)) {
//            return element.value
//        }
        while (true) {
            val curTop = top.value
            val newTop = curTop?.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val PUSH_WAIT_ITERATIONS_COUNT = 100

private enum class State {
    VALUE,
    DONE
}

private class Element<E>(val state: State, val value: E?)