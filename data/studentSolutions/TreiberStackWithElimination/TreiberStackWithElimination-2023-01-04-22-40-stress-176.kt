package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)


    fun push(x: E)
    {
        val randomIndex = (0..ELIMINATION_ARRAY_SIZE).random()
        val castedX = x;
        var i = randomIndex
        while(i < randomIndex)
        {
            if (eliminationArray[randomIndex].compareAndSet(null, castedX)) {
                for (i in 0 until 100) {
                    val value = eliminationArray[randomIndex].value
                    if (value == null || value !== castedX) {
                        return
                    }
                }
                if (!eliminationArray[randomIndex].compareAndSet(castedX, null)) {
                    return
                }
                break
            }
            i++;
        }
    }

    fun pop(): E? {

        return null
    }





    /*fun push1(x: E) {
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
    fun po2p(): E? {
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
    }*/
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT