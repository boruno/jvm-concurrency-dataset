package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {

//        if (isSuccessEliminationPush(x)) {
//            return
//        }

        for (i in (0..COUNT_OF_TRIES / 2)) {

            val index = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            if (eliminationArray[index].compareAndSet(null, x)) {

                for (k in (0..COUNT_OF_TRIES / 2)) {
                    if (eliminationArray[index].value == null) {
                        return
                    }
                }

                if (!eliminationArray[index].compareAndSet(x, null)) {
                    return
                }
                break
            }
        }

        var newHead: Node<E>
        var oldHead: Node<E>?
        do {
            oldHead = top.value
            newHead = Node(x, oldHead)
        } while (!top.compareAndSet(oldHead, newHead))
    }

    private fun isSuccessEliminationPush(x: E): Boolean {
        for (i in (0..COUNT_OF_TRIES / 2)) {

            val index = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            if (eliminationArray[index].compareAndSet(null, x)) {

                for (k in (0..COUNT_OF_TRIES)) {
                    if (eliminationArray[index].compareAndSet(null, null)) {
                        return true
                    }
                }

                if (!eliminationArray[index].compareAndSet(x, null)) {
                    return true
                }
                break
            }
        }

        return false
    }

    private fun isSuccessEliminationPop(): E? {
        for (i in (0..COUNT_OF_TRIES / 2)) {

            val index = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            val currVal = eliminationArray[index].value;

            if (currVal != null && eliminationArray[index].compareAndSet(currVal, null)) {
                return currVal
            }
        }

        return null
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {

//        val res: E? = isSuccessEliminationPop()
//
//        if (res != null) {
//            return res
//        }
        for (i in (0..COUNT_OF_TRIES)) {

            val index = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)

            val currVal = eliminationArray[index].value;

            if (currVal != null && eliminationArray[index].compareAndSet(currVal, null)) {
                return currVal
            }
        }

        var newHead: Node<E>?
        var oldHead: Node<E>?
        do {
            oldHead = top.value
            if (oldHead == null)
                return null
            newHead = oldHead.next
        } while (!top.compareAndSet(oldHead, newHead))
        return oldHead?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT

private const val COUNT_OF_TRIES = 4