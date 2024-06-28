package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.loop
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
    private val eliminationItems = atomicArrayOfNulls<Item<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val xItem = Item(x, ItemStatus.Waiting)
        var isWaiting = false
        var pos = 0
        for (i in 0..CYCLES_TRYING) {
            pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            val cur = eliminationItems[pos].value
            if (cur == null || cur.status == ItemStatus.Empty) {
                if (eliminationItems[pos].compareAndSet(cur, xItem)) {
                    isWaiting = true
                    break
                }
            }
        }

        if (isWaiting) {
            var isBusy = false
            for (i in 0..CYCLES_WAITING) {
                if (eliminationItems[pos].value?.status == ItemStatus.Busy) {
                    isBusy = true
                    break
                }
            }

            val xItemUpd = Item(x, ItemStatus.Empty)
            while (true) {
                val cur = eliminationItems[pos].value
                if (eliminationItems[pos].compareAndSet(cur, xItemUpd)) {
                    if (isBusy) {
                        return
                    } else {
                        break
                    }
                }
            }
        }

        top.loop {
            val updNode = Node(x, it)
            if (top.compareAndSet(it, updNode))
                return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        for (i in 0..CYCLES_TRYING) {
            val pos = ThreadLocalRandom.current().nextInt(ELIMINATION_ARRAY_SIZE)
            val cur = eliminationItems[pos].value
            if (cur?.status == ItemStatus.Waiting) {
                val xItem = Item(cur.x, ItemStatus.Busy)
                if (eliminationItems[pos].compareAndSet(cur, xItem)) {
                    return cur.x
                }
            }
        }
        top.loop {
            if (it == null)
                return null
            if (top.compareAndSet(it, it.next))
                return it.x
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private class Item<E>(val x: E, val status: ItemStatus)
private enum class ItemStatus {
    Empty, Waiting, Busy
}

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val CYCLES_TRYING = 5
private const val CYCLES_WAITING = 10