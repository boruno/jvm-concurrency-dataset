package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    private val SPINS = 12
    private val SPINS_PER_STEP = 6
    private val EMPTY = null
    private val WAITING = Any()
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val node = Node(x, top.value)
            if (node.next === top.value && top.compareAndSet(node.next, node)) return
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
            val slot = eliminationArray[index]
            if (slot.compareAndSet(WAITING, e)) {
                return true
            }
        }
        return false
    }
    private fun awaitComplementary(start: Int, e: E): Boolean {
        var step = 0
        var totalSpins = 0
        while ((step < ELIMINATION_ARRAY_SIZE) && (totalSpins < SPINS)) {
            val index = (start + step) % ELIMINATION_ARRAY_SIZE
            val slot = eliminationArray[index]

            var found = slot.value
            if ((found == WAITING) && slot.compareAndSet(WAITING, e)) {
                return true
            } else if ((found == EMPTY) && slot.compareAndSet(EMPTY, e)) {
                var slotSpins = 0
                while(true) {
                    found = slot.value
                    if (found != e) {
                        return true
                    } else if ((slotSpins >= SPINS_PER_STEP) && (slot.compareAndSet(e, EMPTY))) {
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
            val e: E? = tryReceive()
            if (e != null) {
                return e
            }
        }
    }

    fun tryReceive(): E? {
        val start = getRandomIndex()
        var elem: E? = null
        for (i in 0..ELIMINATION_ARRAY_SIZE) {
            val index: Int = (start + i) % ELIMINATION_ARRAY_SIZE
            val slot = eliminationArray[index]

            val found = slot.value
            if (found !== EMPTY && found !== EMPTY && slot.compareAndSet(found, EMPTY)) {
                elem = found as E
                break
            }
        }
        return elem ?: awaitMatch(start)
    }

    fun awaitMatch(start: Int): E? {
        var step = 0
        var totalSpins = 0
        while (step < ELIMINATION_ARRAY_SIZE && totalSpins < SPINS) {
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
                        } else if (slotSpins >= SPINS_PER_STEP && found === WAITING
                            && slot.compareAndSet(WAITING, EMPTY)
                        ) {
                            // failed to receive an element; try a new slot
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

        // failed to receive an element; give up
        return null
    }


    private fun getRandomIndex(): Int {
        return ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
    }
}

private class Node<E>(val x: E, var next: Node<E>? = null)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT