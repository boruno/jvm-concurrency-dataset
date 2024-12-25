//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.getAndUpdate
import kotlin.random.Random

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    private val rnd = { Random.nextInt(ELIMINATION_ARRAY_SIZE) }

    private fun log(x: Any) {
        val a = eliminationArray[0].getAndUpdate { it }
        val b = eliminationArray[1].getAndUpdate { it }
        println("${Thread.currentThread().id}:\t$x\t[$a, $b]")
    }

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        repeat(ELIMINATION_ARRAY_SIZE) {
            val index = rnd()
            if (eliminationArray[index].compareAndSet(null, x)) {
                repeat(MAX_ROTATIONS) {
                    if (eliminationArray[index].compareAndSet(null, null)) {
                        log("actual> push+pop (elim) : $x")
                        return
                    }
                }

                if (eliminationArray[index].compareAndSet(x, null)) {
                    log("actual> push : $x")

                    do {
                        val before = top.value
                        val after = Node(x, before)
                    } while (!top.compareAndSet(before, after))
                    return
                }

                log("actual> push+pop (elim) : $x")
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun pop(): E? {
        repeat(MAX_ROTATIONS) {
            eliminationArray[rnd()].getAndSet(null)?.let { log("actual> pop (elim) : $it"); return it as E }
        }

        var before: Node<E>?
        do {
            top.value ?: log("actual> pop : null")
            before = top.value ?: return null

            val after = before.next
        } while (!top.compareAndSet(before, after))

        log("actual> pop : ${before?.x}")
        return before?.x
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
private const val MAX_ROTATIONS = 3
