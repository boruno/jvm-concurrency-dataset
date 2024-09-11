package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    fun push(x: E) {
        while (true) {
            val curTop = top.value
            val newTop = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
            else
            {
                val rand = (0..ELIMINATION_ARRAY_SIZE).random()
                if (eliminationArray[rand].compareAndSet(null, "push"+x)) return
                else
                {
                    if (eliminationArray[rand].value == ("pop"+x))
                        return
                }
            }
        }
    }
    fun pop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            val nextTop = curTop.next
            if (top.compareAndSet(curTop, nextTop)) return curTop.x
            else
            {
                val rand = (0..ELIMINATION_ARRAY_SIZE).random()
                if (eliminationArray[rand].value == "push"+curTop.x)
                    return null
                else
                    eliminationArray[rand].compareAndSet(null, "pop"+curTop.x)
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT