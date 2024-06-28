package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min
import kotlin.math.max

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<State, E?>?>(ELIMINATION_ARRAY_SIZE)

    private var retryExponent = 0

    private var stackPushCnt = 0
    private var stackPopCnt = 0

    private var eliminationArrayPushCnt = 0
    private var eliminationArrayPopCnt = 0

    private var tryStartExchangeCnt = 0
    private var retryCheckExchangeCnt = 0

    private var tryPopExchangeCnt = 0

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
            while (stackTryPush()) {
                val curTop = top.value
                val newTop = Node(x, curTop)
                if (top.compareAndSet(curTop, newTop)) {
                    return
                }
            }

            elimination@ while (eliminationArrayTryPush()) {
                val idx = randIdx()
                var exchangeStarted = false

                start_exchange@ while (tryStartExchange()) {
                    val curElem = eliminationArray[idx].value!!

                    if (State.EMPTY == curElem.first) {
                        if (eliminationArray[idx].compareAndSet(curElem, Pair(State.WAITING, x))) {
                            exchangeStarted = true
                            break@start_exchange
                        }
                    }
                }

                if (!exchangeStarted) {
                    continue@elimination
                }

                check_exchange@ while (true) {
                    val curElem = eliminationArray[idx].value!!

                    if (State.WAITING == curElem.first) {
                        if (retryCheckExchange()) {
                            continue@check_exchange
                        }

                        if (eliminationArray[idx].compareAndSet(curElem, Pair(State.EMPTY, null))) {
                            continue@elimination
                        }
                    } else if (State.BUSY == curElem.first) {
                        eliminationArray[idx].value = Pair(State.EMPTY, null)
                        return
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
            while (stackTryPop()) {
                val curTop = top.value ?: return null
                val newTop = curTop.next
                if (top.compareAndSet(curTop, newTop)) {
                    return curTop.x
                }
            }

            while (eliminationArrayTryPop()) {
                val idx = randIdx()

                while (tryExchange()) {
                    val curElem = eliminationArray[idx].value!!

                    if (State.EMPTY == curElem.first) {
                        onNoContention()
                    } else if (State.WAITING == curElem.first) {
                        if (eliminationArray[idx].compareAndSet(curElem, Pair(State.BUSY, null))) {
                            return curElem.second
                        }
                    } else {
                        onContention()
                    }
                }
            }
        }
    }

    private fun randIdx(): Int {
        return (ELIMINATION_ARRAY_SIZE + ThreadLocalRandom.current()
            .nextInt() % ELIMINATION_ARRAY_SIZE) % ELIMINATION_ARRAY_SIZE
    }

    private fun stackTryPush(): Boolean {
        if (stackPushCnt == 1) {
            retryExponent = min(retryExponent + 1, MAX_RETRY_EXPONENT)
            stackPushCnt = 0
            return false
        }

        stackPushCnt += 1
        return true
    }

    private fun stackTryPop(): Boolean {
        if (stackPopCnt == 1) {
            retryExponent = min(retryExponent + 1, MAX_RETRY_EXPONENT)
            stackPopCnt = 0
            return false
        }

        stackPopCnt += 1
        return true
    }

    private fun eliminationArrayTryPush(): Boolean {
        if (eliminationArrayPushCnt >= (2 shl retryExponent)) {
            eliminationArrayPushCnt = 0
            return false
        }

        eliminationArrayPushCnt += 1
        return true
    }

    private fun eliminationArrayTryPop(): Boolean {
        if (eliminationArrayPopCnt >= (2 shl retryExponent)) {
            eliminationArrayPopCnt = 0
            return false
        }

        eliminationArrayPopCnt += 1
        return true
    }

    private fun tryStartExchange(): Boolean {
        if (tryStartExchangeCnt == 1) {
            retryExponent = min(retryExponent + 1, MAX_RETRY_EXPONENT)
            tryStartExchangeCnt = 0
            return false
        }

        tryStartExchangeCnt += 1
        return true
    }

    private fun retryCheckExchange(): Boolean {
        for (_i in 0 until retryExponent) {
            ThreadLocalRandom.current().nextInt()
        }

        if (retryCheckExchangeCnt == (10 * retryExponent)) {
            retryExponent = max(0, retryExponent - 2)
            retryCheckExchangeCnt = 0
            return false
        }

        retryCheckExchangeCnt += 1
        return true
    }

    private fun tryExchange(): Boolean {
        if (tryPopExchangeCnt == 1) {
            tryPopExchangeCnt = 0
            return false
        }

        tryPopExchangeCnt += 1
        return true
    }

    private fun onContention() {
        retryExponent = min(retryExponent + 1, MAX_RETRY_EXPONENT)
    }

    private fun onNoContention() {
        retryExponent = max(0, retryExponent - 2)
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private enum class State {
    EMPTY, WAITING, BUSY,
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val MAX_RETRY_EXPONENT = 5
