//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<ElimElement<E>?>(ELIMINATION_ARRAY_SIZE)
    private val elimIterationsNumber = 100;
    class ElimElement<E> {
        var state: Int = 0 // 1 -- with element, 2 -- done
        var elem: E? = null
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        val el = ElimElement<E>()
        el.state = 1
        el.elem = x
        val empty = ElimElement<E>()

        for (j in 0..elimIterationsNumber) {
            val num = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[num].compareAndSet(null, el)) {
                for (i in 0..100) {
                    if (eliminationArray[num].compareAndSet(empty, null)) {
                        return
                    }
                }
            }

            if (eliminationArray[num].compareAndSet(el, null)) {
                continue
            } else {
                if (eliminationArray[num].compareAndSet(empty, null)) {
                    return
                } else {
                    throw Exception("wtf invalid state");
                }
            }
        }

        while (true) {
            val curent_top = top.value;
            val new_top = Node(x, curent_top)

            if (top.compareAndSet(curent_top, new_top)) {
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
        val empty = ElimElement<E>()

        for (j in 0..elimIterationsNumber) {
            val num = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            val el = eliminationArray[num]

            if (el.value != null && el.value != empty) {
                if (eliminationArray[num].compareAndSet(el.value, empty)) {
                    return el.value!!.elem
                } else {
                    continue
                }
            }
        }

        while (true) {
            val curent_top = top.value;
            var new_top: Node<E>?

            if (curent_top != null) {
                new_top = curent_top.next
            } else {
                new_top = null
            }

            if (top.compareAndSet(curent_top, new_top)) {
                return curent_top?.x
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT