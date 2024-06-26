package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<ThreadInfo<E>?>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        println("push")
        val p = ThreadInfo("push", Node(x, null))
        stackOperation(p)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        println("pop")
        val p = ThreadInfo<E>("pop", null)
        stackOperation(p)
        return p.cell?.x
    }


    private fun stackOperation(threadInfo: ThreadInfo<E>) {
        if (!tryPerformStackOperation(threadInfo)) {
            lesOperation(threadInfo)
        }
    }

    private fun tryPerformStackOperation(p: ThreadInfo<E>): Boolean {
        if (p.op == "push") {
            val currentTop = top.value
            val node = Node(p.cell!!.x, currentTop)
            while (true) {
                if (top.compareAndSet(currentTop, node)) {
                    return true
                }
            }
        } else {
            val currentTop = top.value ?: return true
            val newTop = top.value?.next
            while (true) {
                if (top.compareAndSet(currentTop, newTop)) {
                    p.cell = currentTop
                    return true
                }
            }
        }
    }

    private fun tryCollision(p: ThreadInfo<E>, q: ThreadInfo<E>, pos: Int): Boolean {
        if (p.op == "pop") {
            if (eliminationArray[pos].compareAndSet(q, null)) {
                p.cell = q.cell
                return true
            } else {
                return false
            }
        } else {
            return eliminationArray[pos].compareAndSet(q, p)
        }
    }

    private fun finishCollision(p: ThreadInfo<E>, threadId: Int) {
        if (p.op == "pop") {
            p.cell = eliminationArray[threadId].value!!.cell
            eliminationArray[threadId].value = null
        }
    }

    private fun lesOperation(p: ThreadInfo<E>) {
        val threadId = (Thread.currentThread().id % ELIMINATION_ARRAY_SIZE).toInt()
        while (true) {
            eliminationArray[threadId].value = p
            val pos = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            println("les ${threadId} $p")
            println("les" + eliminationArray[0].value + " " + eliminationArray[1].value + " " + pos)
            val q = eliminationArray[pos].value
            if (q != null) {
                if ((q as ThreadInfo<*>).op != (p as ThreadInfo<*>).op) {
                    if (eliminationArray[threadId].compareAndSet(p, null)) {
                        if (tryCollision(p, q, pos)) {
                            return
                        } else {
                            if (tryPerformStackOperation(p)) {
                                return
                            }
                        }
                    }
                }
            }

            Thread.sleep(10)
            if (!eliminationArray[threadId].compareAndSet(p, null)) {
                finishCollision(p, threadId)
                return
            }

            if (tryPerformStackOperation(p)) {
                return
            }
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)
private data class ThreadInfo<E>(val op: String, var cell: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT