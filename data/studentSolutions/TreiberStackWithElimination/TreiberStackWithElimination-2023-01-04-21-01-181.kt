package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    fun push(x: E) {
        while (true) {
            val rand = (0..1).random();
            val step = 0
            var check = false
            while(step < 1000) {
                if (eliminationArray[rand].compareAndSet(777, 0))
                {
                    check = true
                }
            }
            if (check == false) {
                val curTop = top.value
                val newTop = Node<E>(x, curTop)
                if (top.compareAndSet(curTop, newTop)) return
            }
        }
    }
    fun pop(): E? {
        while (true) {
            val curTop = top.value ?: return null
            val nextTop = curTop.next
            if (top.compareAndSet(curTop, nextTop)) {
                val rand = (0..1).random()
                eliminationArray[rand].value = 777
                return curTop.x
            }
        }
        return null
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT