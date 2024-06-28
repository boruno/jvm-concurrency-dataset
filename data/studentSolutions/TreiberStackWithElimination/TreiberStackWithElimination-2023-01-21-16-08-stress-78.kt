package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.atomic.AtomicReference


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)
    val SPINS = 12
    val SPINS_PER_STEP = 6
    val ARENA_MASK = ELIMINATION_ARRAY_SIZE - 1
    val FREE: Any? = null
    val WAITER = Any()
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val node = Node<E>(x)
        while (true) {
            node.next = top.value

            // Attempt to push to the stack, backing off to the elimination array if contended
            if (top.value === node.next && top.compareAndSet(node.next, node)) {
                return
            }
            if (tryTransfer(x)) {
                return
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
            val current: Node<E> = top.value ?: return null

            // Attempt to pop from the stack, backing off to the elimination array if contended
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
        val start = startIndex()
        val e: E? = scanAndMatch(start)
        return e ?: awaitMatch(start)
    }

    fun scanAndMatch(start: Int): E? {
        for (i in 0..ELIMINATION_ARRAY_SIZE) {
            val index: Int = (start + i) and ARENA_MASK
            val slot = eliminationArray[index]

            // accept a transfer if an element is available
            val found = slot.value
            if (found !== FREE && found !== WAITER && slot.compareAndSet(found, FREE)) {
                return found as E
            }
        }
        return null
    }

    fun awaitMatch(start: Int): E? {
        var step = 0
        var totalSpins = 0
        while (step < ELIMINATION_ARRAY_SIZE && totalSpins < SPINS) {
            val index = start + step and ARENA_MASK
            val slot = eliminationArray[index]
            var found = slot.value
            if (found === FREE) {
                if (slot.compareAndSet(FREE, WAITER)) {
                    var slotSpins = 0
                    while (true) {
                        found = slot.value
                        if (found !== WAITER && slot.compareAndSet(found, FREE)) {
                            return found as E
                        } else if (slotSpins >= SPINS_PER_STEP && found === WAITER
                            && slot.compareAndSet(WAITER, FREE)
                        ) {
                            // failed to receive an element; try a new slot
                            totalSpins += slotSpins
                            break
                        }
                        slotSpins++
                    }
                }
            } else if (found !== WAITER && slot.compareAndSet(found, FREE)) {
                return found as E
            }
            step++
        }

        // failed to receive an element; give up
        return null
    }

    fun tryTransfer(e: E): Boolean {
        val start: Int = startIndex()
        return scanAndTransferToWaiter(e, start) || awaitExchange(e, start)
    }
    fun scanAndTransferToWaiter(e: E, start: Int): Boolean {
        for (i in 0..ELIMINATION_ARRAY_SIZE) {
            val index = (start + i) and (ARENA_MASK)
            val slot = eliminationArray[index]
            // if some thread is waiting to receive an element then attempt to provide it
            if (slot.value === WAITER && slot.compareAndSet(WAITER, e)) {
                return true
            }
        }
        return false
    }

    private fun startIndex(): Int {
        val id = Thread.currentThread().id
        return ((id xor (id ushr 32)).toInt() xor -0x7ee3623b) * 0x01000193
    }

    fun awaitExchange(e: E, start: Int): Boolean {
        var step = 0
        var totalSpins = 0
        while ((step < ELIMINATION_ARRAY_SIZE) && (totalSpins < SPINS)) {
            val index = (start + step) and ARENA_MASK
            val slot = eliminationArray[index]

            var found = slot.value
            if ((found == WAITER) && slot.compareAndSet(WAITER, e)) {
                return true
            } else if ((found == FREE) && slot.compareAndSet(FREE, e)) {
                var slotSpins = 0
                while(true) {
                    found = slot.value
                    if (found != e) {
                        return true
                    } else if ((slotSpins >= SPINS_PER_STEP) && (slot.compareAndSet(e, FREE))) {
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
}

private class Node<E>(val x: E, var next: Node<E>? = null)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT