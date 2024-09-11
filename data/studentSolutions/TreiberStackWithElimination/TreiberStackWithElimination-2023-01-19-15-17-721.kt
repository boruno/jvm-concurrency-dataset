package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom


/*class TreiberStackWithElimination<E> {
    private val ELIMINATION_ITERATING = 2

    inner class Node constructor(x: E, next: Node?) {
        val next: AtomicRef<Node?>
        val x: E

        init {
            this.next = atomic(next)
            this.x = x
        }
    }

    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val eliminationStates = AtomicIntArray(ELIMINATION_ARRAY_SIZE)

    // head pointer
    private val head = atomic<Node?>(null)

    fun push(x: E) {
        var eliminationIndex = -1
        var y: Int = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var i = 0
        while (i < ELIMINATION_ITERATING) {
            if (y == ELIMINATION_ARRAY_SIZE) {
                y = 0
            }
            if (eliminationStates[y].compareAndSet(0, 5)) {
                eliminationArray[y].value = x
                eliminationStates[y].value = 1
                eliminationIndex = y
                break
            }
            i++
            y++
        }
        while (true) {
            val curHead = head.value
            val futureHead = Node(x, curHead)
            if (eliminationIndex >= 0) {
                if (eliminationStates[eliminationIndex].compareAndSet(1, 2)) {
                    if (head.compareAndSet(curHead, futureHead)) {
                        eliminationStates[eliminationIndex].value = 0
                        return
                    } else {
                        eliminationStates[eliminationIndex].value = 1
                    }
                } else if (eliminationStates[eliminationIndex].value == 4) {
                    return
                }
            } else {
                if (head.compareAndSet(curHead, futureHead)) {
                    return
                }
            }
        }
    }

    fun pop(): E? {
        var y: Int = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var i = 0
        while (i < ELIMINATION_ITERATING) {
            if (y == ELIMINATION_ARRAY_SIZE) {
                y = 0
            }
            if (eliminationStates[y].compareAndSet(1, 3)) {
                val k = eliminationArray[y].value
                eliminationStates[y].value = 4
                return k
            }
            i++
            y++
        }
        while (true) {
            val curHead = head.value ?: return null
            val futureHead = curHead.next.value
            if (head.compareAndSet(curHead, futureHead)) {
                return curHead.x
            }
        }
    }
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT*/

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<String, E?>?>(ELIMINATION_ARRAY_SIZE)


/**
     * Adds the specified element [x] to the stack.
     */

    fun push(x: E) {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var puttedToElimination = false
        // цикл ограничен максимальным числом итераций, потому что
        // если elimination долгий нужно пытаться честно вставить элемент в стек
        for(i in 0..MAX_ELIMINATION_ITERATIONS) {
            val eliminationElement = eliminationArray[eliminationIndex]
            if(eliminationElement.compareAndSet(null, Pair("PROCESSING", x))) {
                puttedToElimination = true
                break
            }
        }

        if (puttedToElimination) {
            for(i in 0..MAX_ELIMINATION_ITERATIONS) {
                if (eliminationArray[eliminationIndex].compareAndSet(Pair("DONE", null), null)) return
            }
            val eliminationElement = eliminationArray[eliminationIndex]
            eliminationArray[eliminationIndex].compareAndSet(eliminationElement.value, null);
        }


        while (true) {
            val currTop = top.value
            val newTop = Node(x, currTop)
            if (top.compareAndSet(currTop, newTop)) return
        }
    }


/**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */

    fun pop(): E? {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in 0..MAX_ELIMINATION_ITERATIONS) {
            val x = eliminationArray[eliminationIndex].value
            if (x?.second != null) {
                if (eliminationArray[eliminationIndex].compareAndSet(x, Pair("DONE", null))) {
                    return x.second
                }
            }
        }

        while (true) {
            val currTop = top.value ?: return null
            val newTop = currTop.next
            if (top.compareAndSet(currTop, newTop)) return currTop.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val MAX_ELIMINATION_ITERATIONS = 6
