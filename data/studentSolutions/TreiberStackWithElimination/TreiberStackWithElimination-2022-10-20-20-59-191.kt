//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<State, E?>?>(ELIMINATION_ARRAY_SIZE)

    init {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            eliminationArray[i].value = Pair(State.EMPTY, null)
        }
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node(x, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                return
            }

            val elem = Pair(State.WAITING, x)

            elimination@ for (_i in 0 until 2) {
                val idx = randIdx()
                var exchangeStarted = false

                start_exchange@ for (_j in 0 until 10) {
                    val curElem = eliminationArray[idx].value!!

                    if (State.EMPTY == curElem.first) {
                        if (eliminationArray[idx].compareAndSet(curElem, elem)) {
                            exchangeStarted = true
                            break@start_exchange
                        }
                    }
                }

                if (!exchangeStarted) {
                    continue@elimination
                }

                var i = 0
                exchange@ while (true) {
                    val curElem = eliminationArray[idx].value!!

                    if (State.WAITING == curElem.first) {
                        if (i < 10) {
                            i++
                            continue@exchange
                        }

                        if (eliminationArray[idx].compareAndSet(curElem, Pair(State.EMPTY, null))) {
                            continue@elimination
                        }
                    } else if (State.BUSY == curElem.first) {
                        eliminationArray[idx].value = Pair(State.EMPTY, null)
                    }
                }
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
            val curTop = top.value ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.x
            }

            elimination@ for (_i in 0 until 2) {
                val idx = randIdx()

                exchange@ for (_j in 0 until 10) {
                    val curElem = eliminationArray[idx].value!!

                    if (State.WAITING == curElem.first) {
                        if (eliminationArray[idx].compareAndSet(curElem, Pair(State.BUSY, null))) {
                            return curElem.second
                        }
                    }
                }
            }
        }
    }

    private fun randIdx(): Int {
        return (ELIMINATION_ARRAY_SIZE + ThreadLocalRandom.current()
            .nextInt() % ELIMINATION_ARRAY_SIZE) % ELIMINATION_ARRAY_SIZE
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private enum class State {
    EMPTY, WAITING, BUSY,
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT