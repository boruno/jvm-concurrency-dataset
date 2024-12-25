//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val node = Node(x, top.value)
            if (top.value === node.next && top.compareAndSet(node.next, node)) return
            if (tryElimination(x)) return
        }
    }

    private fun tryElimination(e: E): Boolean {
        val i = getRandomIndex()
        // либо добавляем наш элемент в ждущий pop,
        // либо ждем когда на этом элементе вызовется pop
        return connectWithWaiting(i, e) || awaitComplementary(i, e)
    }
    private fun connectWithWaiting(start: Int, e: E): Boolean {
        for (i in 0..ELIMINATION_ARRAY_SIZE) {
            val index = (start + i) % ELIMINATION_ARRAY_SIZE
            val curElem = eliminationArray[index]
            if (curElem.compareAndSet(WAITING, e)) return true
        }
        return false
    }
    private fun awaitComplementary(start: Int, e: E): Boolean {
        var step = 0
        var totalSpins = 0
        while ((step < ELIMINATION_ARRAY_SIZE) && (totalSpins < ITERATIONS)) {
            val index = (start + step) % ELIMINATION_ARRAY_SIZE
            val curElem = eliminationArray[index]
            var curValue = curElem.value
            if (curElem.compareAndSet(WAITING, e)) {
                return true
            } else if (curElem.compareAndSet(EMPTY, e)) {
                var slotSpins = 0
                while(true) {
                    curValue = curElem.value
                    if (curValue != e) {
                        return true
                    } else if ((slotSpins >= ITERATIONS_PER_STEP) && (curElem.compareAndSet(e, EMPTY))) {
                        // failed to transfer the element; try a new slot
                        totalSpins += slotSpins
                        break
                    }
                    slotSpins++
                }
            }
            step++
        }
        return false
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val current: Node<E> = top.value ?: return null
            if (top.compareAndSet(current, current.next)) {
                return current.x
            }
            var elem: E? = null
            val start = getRandomIndex()
            for (i in 0..ELIMINATION_ARRAY_SIZE) {
                val index: Int = (start + i) % ELIMINATION_ARRAY_SIZE
                val curElem = eliminationArray[index]

                val curValue = curElem.value
                if (curValue !== EMPTY && curValue !== WAITING && curElem.compareAndSet(curValue, EMPTY)) {
                    elem = curValue as E
                    break
                }
            }
            elem = elem ?: awaitMatch(start)
            if (elem != null) {
                return elem
            }
        }
    }

    fun awaitMatch(start: Int): E? {
        var step = 0
        var totalSpins = 0
        while (step < ELIMINATION_ARRAY_SIZE && totalSpins < ITERATIONS) {
            val index = (start + step) % ELIMINATION_ARRAY_SIZE
            val slot = eliminationArray[index]
            var found = slot.value
            if (found === EMPTY) {
                if (slot.compareAndSet(EMPTY, WAITING)) {
                    var slotSpins = 0
                    while (true) {
                        found = slot.value
                        if (found !== WAITING && slot.compareAndSet(found, EMPTY)) {
                            return found as E
                        } else if (slotSpins >= ITERATIONS_PER_STEP && found === WAITING
                            && slot.compareAndSet(WAITING, EMPTY)
                        ) {
                            totalSpins += slotSpins
                            break
                        }
                        slotSpins++
                    }
                }
            } else if (found !== WAITING && slot.compareAndSet(found, EMPTY)) {
                return found as E
            }
            step++
        }
        return null
    }


    private fun getRandomIndex(): Int {
        return ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
    }
}

private class Node<E>(val x: E, var next: Node<E>? = null)

const val ITERATIONS = 12
const val ITERATIONS_PER_STEP = 6
val EMPTY = null
val WAITING = Any()

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT