//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<ElimElement<E>?>(ELIMINATION_ARRAY_SIZE)
    private val elimIterationsNumber = 10;
//    val empty = ElimElement<E>()
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

        for (j in 0..10) {
            val num = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            if (eliminationArray[num].compareAndSet(null, el)) {
                for (i in 0..100) {
                    val aboba = eliminationArray[num].value

                    if (aboba != null) {
                        if (aboba.state != 0) {
                            continue
                        }
                    } else {
                        throw Exception("dead aboba");
                    }

                    if (eliminationArray[num].compareAndSet(aboba, null)) {
                        return
                    }
                }

                if (eliminationArray[num].compareAndSet(el, null)) {
                    continue
                } else {
                    val aboba = eliminationArray[num].value

                    if (aboba == null || aboba.state != 0) {
                        throw Exception("wtf invalid not zero");
                    }
                    if (eliminationArray[num].compareAndSet(aboba, null)) {
                        return
                    } else {
                        throw Exception("wtf invalid state");
                    }
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
        for (j in 0..100) {
            val num = Random.nextInt(0, ELIMINATION_ARRAY_SIZE)
            val el = eliminationArray[num].value

            if (el != null && el.state != 0) {
                if (eliminationArray[num].compareAndSet(el, empty)) {
                    return el.elem
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