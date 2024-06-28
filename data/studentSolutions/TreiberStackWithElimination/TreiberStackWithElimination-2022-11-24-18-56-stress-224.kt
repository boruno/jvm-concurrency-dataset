package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

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
        for (retries in 0 until TRY_COUNT) {
            val value = eliminationArray[pos].value
            if (value != null &&
                eliminationArray[pos].compareAndSet(value, null)) {
                return Pair(true, value)
            }

            pos = (1 + pos) % ELIMINATION_ARRAY_SIZE
        }

        return Pair(false, null)
    }

    private fun eliminatorPush(x: E): Boolean {
        var pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

        var value = eliminationArray[pos].value
        var retries = 0
        while (true) {
            if (retries >= TRY_COUNT) {
                break
            }

            if (value == null &&
                eliminationArray[pos].compareAndSet(null, x)) {
                break
            }

            pos = (1 + pos) % ELIMINATION_ARRAY_SIZE
            value = eliminationArray[pos].value
            retries++
        }
        if (retries == TRY_COUNT + 1) {
            return false
        }
        retries = 0
        while (retries < TRY_COUNT) {
            if (eliminationArray[pos].value == null || eliminationArray[pos].value != x) {
                return true
            }
            retries++
        }

        return eliminationArray[pos].getAndSet(null) != x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val TRY_COUNT = 1