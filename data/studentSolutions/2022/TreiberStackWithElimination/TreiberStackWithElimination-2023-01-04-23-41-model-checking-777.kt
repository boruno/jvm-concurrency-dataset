package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)


    fun push(x: E) {
        val eliminationRandomIndex = Random(ELIMINATION_ARRAY_SIZE).nextInt()
        //for (index2 in eliminationRandomIndex until eliminationRandomIndex) {
            if (eliminationArray[eliminationRandomIndex].compareAndSet(null, x)) {
                repeat(REPEATS) {
                    val value = eliminationArray[eliminationRandomIndex].value ?: return
                    if (value !== x) return
                }
                if (!eliminationArray[eliminationRandomIndex].compareAndSet(x, null)) {
                    return
                }
                //break
            }
       // }

        while (true) {
            val curTop = top.value
            val newTop = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    fun pop(): E? {
        val eliminationRandomIndex = Random(ELIMINATION_ARRAY_SIZE).nextInt()
        //for (index2 in eliminationRandomIndex until eliminationRandomIndex) {
            val value = eliminationArray[eliminationRandomIndex].value ?: return null
            if (eliminationArray[eliminationRandomIndex].compareAndSet(value, null)) return null
        //}

        while (true) {
            val curTop = top.value ?: return null
            val nextTop = curTop.next
            if (top.compareAndSet(curTop, nextTop)) return curTop.x
        }
    }

}
private class Node<E>(val x: E, val next: Node<E>?)
private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val REPEATS = 100