//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls


class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Int?>(ELIMINATION_ARRAY_SIZE)


    fun push(x: E)
    {
        val randomIndex = (0..(ELIMINATION_ARRAY_SIZE - ITERATION_AROUND * 2) + ITERATION_AROUND).random()
        val castedX = x.toString().toInt();

        for (j in randomIndex until randomIndex) {
            if (eliminationArray[j].compareAndSet(null, castedX)) {
                for (i in 0 until 50) {
                    val value = eliminationArray[j].value
                    if (value == null || value !== castedX) {
                        return
                    }
                }
                if (!eliminationArray[j].compareAndSet(castedX, null)) {
                    return
                }
                break
            }
        }

        while (true)
        {
            val curTop = top.value
            val newTop = Node<E>(x, curTop)
            if (top.compareAndSet(curTop, newTop)) return
        }
    }

    fun pop(): Int?
    {
        val randomIndex = (0..(ELIMINATION_ARRAY_SIZE - ITERATION_AROUND * 2) + ITERATION_AROUND).random()

        for (j in randomIndex until randomIndex) {
            val value = eliminationArray[j].value
            if (value != null && eliminationArray[j].compareAndSet(value, null)) {
                return value
            }
        }

        while (true)
        {
            val curTop = top.value ?: return null
            val nextTop = curTop.next
            if (top.compareAndSet(curTop, nextTop)) return curTop.x.toString().toInt()
        }
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
private const val ITERATION_AROUND = 3
private const val ITERATION_WAIT = 20