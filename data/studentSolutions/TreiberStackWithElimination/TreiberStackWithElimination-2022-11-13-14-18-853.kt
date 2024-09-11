package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.TimeUnit

class TreiberStackWithElimination<E>() {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E>>(ELIMINATION_ARRAY_SIZE)

    private enum class State {
        FREE, WAITING, FINISHED
    }

    private class Operation<E>(val state: State, val node: Node<E>?)
    private val noop = Operation<E>(State.FREE, null)
    private val done = Operation<E>(State.FINISHED, null)

    init {
        for (i in (0 until ELIMINATION_ARRAY_SIZE)) {
            eliminationArray[i].value = noop
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val head = top.value
            val node = Node(x, head)
            if (top.compareAndSet(head, node)) {
                return

            } else {
                // first CAS failed, do back-off
                val ind = (0 until ELIMINATION_ARRAY_SIZE).random()
                val op = Operation(State.WAITING, node)

                // find a random place in an elimination array
                if (eliminationArray[ind].compareAndSet(noop, op)) {

                    // if succeeded, wait for it's done
                    /*for (i in (1..50)) {
                        if (eliminationArray[ind].compareAndSet(done, noop)) {
                            return;
                        }
                    }*/

                    eliminationArray[ind].compareAndSet(op, noop);
                }

                // sleep for 0.2 millisecond and try again
                /*try {
                    TimeUnit.MICROSECONDS.sleep(200)
                } catch (_: InterruptedException) {}*/
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val head = top.value ?: return null
            if (top.compareAndSet(head, head.next)) {
                return head.x

            } /*else {
                // first CAS failed, do back-off
                val ind = (0 until ELIMINATION_ARRAY_SIZE).random()
                val op = eliminationArray[ind].value

                // found its pair
                if (op!!.state == State.WAITING) {
                    if (eliminationArray[ind].compareAndSet(op, done)) {
                        return op.node!!.x;
                    }
                }

                // failure, try again
                try {
                    TimeUnit.MICROSECONDS.sleep(200)
                } catch (_: InterruptedException) {}
            }*/
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT