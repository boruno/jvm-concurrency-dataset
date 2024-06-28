package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Cell<E>?>(ELIMINATION_ARRAY_SIZE)

    fun stackPush(x : E)
    {
        while (true) {
            val cur_top = top.value
            val new_top = Node(x, cur_top)
            if (top.compareAndSet(cur_top, new_top)) return
        }
    }

    fun push(x: E)
    {
        val newCell = Cell(x)
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        val randomCell = eliminationArray.get(index).value
        if (randomCell == null) {
            if (eliminationArray.get(index).compareAndSet(null, newCell)) {
                if (eliminationArray.get(index).compareAndSet(newCell, null))
                    stackPush(x)
            } else stackPush(x)
        } else stackPush(x)
    }

    fun pop(): E?
    {
        val index = Random.nextInt(ELIMINATION_ARRAY_SIZE)
        while (true) {
            val randomCell = eliminationArray.get(index).value
            if (randomCell == null) {
                while (true) {
                    val cur_top = top.value
                    val new_top = cur_top?.next
                    if (top.compareAndSet(cur_top, new_top))
                        return cur_top?.x
                }
            } else randomCell.x
        }
    }
}

private class Cell<E>(val x: E)

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT