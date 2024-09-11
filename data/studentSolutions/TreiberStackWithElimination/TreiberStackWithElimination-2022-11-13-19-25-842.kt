package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Operation<E?>>(ELIMINATION_ARRAY_SIZE)

    init {
        for (x in (0 until ELIMINATION_ARRAY_SIZE)) {
            eliminationArray[x].getAndSet(Operation(Status.EMPTY, null))
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (eliminatorPush(x)) {
            return
        }

        pushToStack(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val (isSuccess, value) = eliminatorPop()
        if (isSuccess) {
            return value
        }

        return popFromStack()
    }

    private fun pushToStack(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }
        }
    }

    private fun popFromStack(): E? {
        while (true) {
            val curTop = top.value ?: return null

            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }
        }
    }

    private fun eliminatorPop(): Pair<Boolean, E?> {
        var pos: Int = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var res: E?
        for (retries in 0 until TRY_COUNT) {
            if (takeIfFilled(pos).also { res = it } != null) {
                return Pair(true, res)
            }
            pos = (1 + pos) % ELIMINATION_ARRAY_SIZE
        }

        return Pair(false, null)
    }

    private fun takeIfFilled(pos: Int): E? {
        while (true) {
            val operation = eliminationArray[pos].value

            if (operation == null || operation.status != Status.FILLED) {
                break
            }

            if (eliminationArray[pos].compareAndSet(operation, Operation(Status.TAKEN, operation.value))) {
                return operation.value
            }
        }
        return null
    }

    private fun eliminatorPush(x: E): Boolean {
        var pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

        var operation = eliminationArray[pos].value
        var retries = 0
        while (true) {
            if (retries >= TRY_COUNT) {
                break
            }

            if (operation != null &&
                operation.status == Status.EMPTY &&
                eliminationArray[pos].compareAndSet(operation, Operation(Status.FILLED, x))) {
                break
            }

            pos = (1 + pos) % ELIMINATION_ARRAY_SIZE
            operation = eliminationArray[pos].value
            retries++
        }
        if (retries == TRY_COUNT) {
            return false
        }
        retries = 0
        while (retries < TRY_COUNT) {
            if (operation != null &&
                operation.status == Status.TAKEN &&
                eliminationArray[pos].compareAndSet(operation, Operation(Status.EMPTY, null))) {
                return true
            }
            retries++
        }
        return operation != null &&
               operation.status == Status.TAKEN &&
               eliminationArray[pos].compareAndSet(operation, Operation(Status.EMPTY, null))
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private enum class Status {
    EMPTY, FILLED, TAKEN
}
private class Operation<E>(val status: Status, val value: E?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val TRY_COUNT = 5