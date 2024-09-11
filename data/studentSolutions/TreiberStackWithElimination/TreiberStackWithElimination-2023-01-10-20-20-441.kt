package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    private val FOR_REPEAT_DELAY_COUNT: Int = 1000

    private fun baseStackPush(x: E) {
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top)) return
        }
    }

    private fun baseStackPop(): E? {
        while (true) {
            val cur_top = top.value ?: return null
            val new_top = cur_top.next
            if (top.compareAndSet(cur_top, new_top)) cur_top.x
        }
    }

    private fun getRandomEliminationElement(): Int = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val randomIdx = getRandomEliminationElement()
        val randomCell = eliminationArray[randomIdx]
        val currRandomValue = eliminationArray[randomIdx].value
        if (currRandomValue == null) {
            if (eliminationArray[randomIdx].compareAndSet(null, x)) {
                //
                repeat(FOR_REPEAT_DELAY_COUNT) {}

                if (!eliminationArray[randomIdx].compareAndSet(x, null)) {
                    return
                } else baseStackPush(x)
            } else baseStackPush(x)
        } else baseStackPush(x)
        //baseStackPush(x)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val randomIdx = getRandomEliminationElement()
        val randomCell = eliminationArray[randomIdx]

        while (true) {
            val currValue = eliminationArray[randomIdx].value // ?: return baseStackPop()

            if (currValue == null) return baseStackPop()

            if (eliminationArray[randomIdx].compareAndSet(currValue, null)) {
                return currValue
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT