//package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

enum class EliminationKind {
    PUSH, POP
}

class Elimination<E> (val kind: EliminationKind, var item: E?) {
}

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Elimination<E>?>(ELIMINATION_ARRAY_SIZE)


    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            // old
            var newHead: Node<E>?
            var cur: Node<E>?
            cur = top.value
            newHead = Node<E>(x, cur)
            if(top.compareAndSet(cur, newHead)) {
                return
            }
            // new
            val index: Int = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val curElimination = eliminationArray[index].value
            val elimination = eliminationArray[index].value
            if (elimination != null) {
                if (elimination.kind == EliminationKind.PUSH) {
                    continue
                }
                if (elimination.kind == EliminationKind.POP) {
                    elimination.item = x
                    if (eliminationArray[index].compareAndSet(curElimination, elimination)) {
                        return
                    }
                    continue
                }
            }
            val newElimination = Elimination(EliminationKind.PUSH, x)
            if (!eliminationArray[index].compareAndSet(elimination, newElimination)) {
                continue
            }
            for (i in 0..100) {
                if (eliminationArray[index].value != newElimination) {
                    return
                }
            }
            if (eliminationArray[index].compareAndSet(newElimination, null)) {
                continue
            }
            return
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            // old
            var newHead: Node<E>?
            var cur: Node<E>?
            cur = top.value
            if (cur == null) return null
            newHead = cur.next
            if (top.compareAndSet(cur, newHead)) return cur.x
            // new
            val index: Int = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            val elimination = eliminationArray[index].value
            if (elimination != null) {
                if (elimination.kind == EliminationKind.POP) {
                    continue
                }
                if (elimination.kind == EliminationKind.PUSH) {
                    if (eliminationArray[index].compareAndSet(elimination, null)) {
                        return elimination.item
                    }
                }

            }
            val newElimination = Elimination<E>(EliminationKind.POP, null)
            if (!eliminationArray[index].compareAndSet(elimination, newElimination)) {
                continue
            }
            for (i in 0..100) {
                if (eliminationArray[index].value != newElimination) {
                    return newElimination.item
                }
            }
            if (eliminationArray[index].compareAndSet(newElimination, null)) {
                continue
            }
            return newElimination.item

        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT