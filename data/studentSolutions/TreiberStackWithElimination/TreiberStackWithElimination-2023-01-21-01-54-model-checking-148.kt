package mpp.stackWithElimination

import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import mpp.stackWithElimination.State.*
import java.util.concurrent.ThreadLocalRandom


class TreiberStackWithElimination<E> {
    inner class Node (x: E, next: Node?) {
        val next = atomic(next)
        val x = x
    }

    // head pointer
    private val SIZE = 15
    private val head = atomic<Node?>(null)
    private val elimination = atomicArrayOfNulls<Node?>(SIZE)

    private fun getPosition(index: Int): Int {
        return (index + SIZE) % SIZE
    }

    fun push(x: E) {
        val input = Node(x, null)
        val index: Int = ThreadLocalRandom.current().nextInt() % SIZE
        for (i in 0..SIZE) {
            val position = getPosition(index + i)
            if (elimination[position].compareAndSet(null, input)) {
                for (j in 0..9) {
                    if (elimination[position].value == null) {
                        return
                    }
                }
                if (!elimination[position].compareAndSet(input, null)) {
                    return
                }
                break
            }
        }
        while (true) {
            val expected = head.value
            val update = Node(x, expected)
            if (head.compareAndSet(expected, update)) {
                return
            }
        }
    }

    fun pop(): E? {
        val index: Int = ThreadLocalRandom.current().nextInt() % SIZE
        for (i in 0..SIZE) {
            val position = getPosition(index + i)
            val value = elimination[position].value
            if (value != null) {
                if (elimination[position].compareAndSet(value, null)) {
                    return value.x
                }
            }
        }
        while (true) {
            val current = head.value ?: return null
            if (head.compareAndSet(current, current.next.value)) {
                return current.x
            }
        }
    }
    /*private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val eliminationStates = atomicArrayOfNulls<State>(ELIMINATION_ARRAY_SIZE)
    init {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) eliminationStates[i].value = EMPTY
    }


    *//**
     * Adds the specified element [x] to the stack.
     *//*

    fun push(x: E) {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        var puttedToElimination = false
        // цикл ограничен максимальным числом итераций, потому что
        // если elimination долгий нужно пытаться честно вставить элемент в стек
        for (i in 0..MAX_ELIMINATION_ITERATIONS) {
            if (eliminationStates[eliminationIndex].compareAndSet(EMPTY, BUSY)) {
                eliminationArray[eliminationIndex].value = x
                eliminationStates[eliminationIndex].value = WAITING
                puttedToElimination = true
                break
            }
        }
        while (true) {
            //if(eliminationStates[eliminationIndex].value == BUSY) break
            val curHead = top.value
            val futureHead = Node(x, curHead)
            if (puttedToElimination) {
                if (eliminationStates[eliminationIndex].compareAndSet(WAITING, BUSY)) {
                    if (top.compareAndSet(curHead, futureHead)) {
                        eliminationStates[eliminationIndex].value = EMPTY
                        return
                    } else {
                        eliminationStates[eliminationIndex].value = WAITING
                    }
                } else if (eliminationStates[eliminationIndex].value == PREPOP) {
                    return
                } else if (eliminationStates[eliminationIndex].value == BUSY) {
                    return
                }
            } else {
                if (top.compareAndSet(curHead, futureHead)) {
                    return
                }
            }
        }
    }


    *//**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     *//*

    fun pop(): E? {
        val eliminationIndex = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
        for (i in 0..MAX_ELIMINATION_ITERATIONS) {
            if (eliminationStates[eliminationIndex].compareAndSet(WAITING, BUSY)) {
                val k = eliminationArray[eliminationIndex]
                eliminationStates[eliminationIndex].value = PREPOP
                return k.value
            }
        }

        while (true) {
            val currTop = top.value ?: return null
            val newTop = currTop.next
            if (top.compareAndSet(currTop, newTop)) return currTop.x
        }
    }*/
}

enum class State{
    EMPTY, WAITING, BUSY, PREPOP;
}
//private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val MAX_ELIMINATION_ITERATIONS = 3