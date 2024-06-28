package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom




class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    private val FIND_ATTEMPTS = 4
    private val WAIT_LOOP_ITERS = 100_000

    private fun getRandomIndex(): Int {
        return ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
    }

    private fun wait_loop() {
        for (i in 0 until WAIT_LOOP_ITERS);
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        if (try_elimination_push(x)) return
        base_push(x)
    }

    private fun try_elimination_push(x: E): Boolean {
        val index = getRandomIndex()

        for (attempt in 0 until FIND_ATTEMPTS) {

            val curIndex: Int = (index + attempt) % ELIMINATION_ARRAY_SIZE
            val el = eliminationArray.get(curIndex)

            if (el.compareAndSet(null, x)) {
                wait_loop()
                return !el.compareAndSet(x, null)
            }
        }
        return false
    }


    private fun base_push(x: E) {
        var newHead: Node<E>?
        var oldHead: Node<E>?
        do {
            oldHead = top.value
            newHead = Node(x,oldHead)
        } while (!top.compareAndSet(oldHead, newHead))
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        return try_elimination_pop() ?: base_pop()
    }
    private fun try_elimination_pop() : E? {
        val index = getRandomIndex()
        for (attempt in 0 until FIND_ATTEMPTS) {
            val curIndex: Int = (index + attempt) % ELIMINATION_ARRAY_SIZE
            val el = eliminationArray.get(curIndex)
            if (el.value != null) {
                return el.value
            }
        }
        return null
    }
    private fun base_pop(): E? {
        var oldHead: Node<E>?
        var newHead: Node<E>?
        do {
            oldHead = top.value
            if (oldHead == null) return null
            newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return oldHead?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT