package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlin.random.Random

enum class EliminationKind {
    PUSH, POP
}

data class Elimination<E> (val kind: EliminationKind, var item: E?) {
    fun isAwaitingPop(): Boolean {
        return kind == EliminationKind.POP && item == null
    }

    fun isAwaitingPush(): Boolean {
        return kind == EliminationKind.PUSH && item != null
    }

}

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Elimination<E>?>(ELIMINATION_ARRAY_SIZE)
    /**
     * null -- no operation
     * {kind: pop}:
     *      {item: null} -- awaiting pop
     *      {item: x   } -- successful pop
     * {kinf: push}:
     *      {item: null} -- successful push
     *      {item: x   } -- awaiting push  
     */

    fun trieberPush(x: E): Boolean {
        val cur = top.value
        val newHead = Node<E>(x, cur)
        return top.compareAndSet(cur, newHead)
    }

    fun eliminationPush(x: E): Boolean {
        var randomIndex: Int = 0
        var elimination: Elimination<E>? = null
        for (i in (0 until 5)) {
            randomIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            elimination = eliminationArray[randomIndex].value
            if (elimination != null && elimination.isAwaitingPop()) break
        }
        if (elimination != null) {
            val updatedPopElimination = elimination.copy(item = x)
            return eliminationArray[randomIndex].compareAndSet(elimination, updatedPopElimination)
        }
        val pushElimination = Elimination<E>(EliminationKind.PUSH, x)
        if (!eliminationArray[randomIndex].compareAndSet(null, pushElimination)) return false
        var updatedPushElimination: Elimination<E>?
        for (i in (0 until 10)) {
            updatedPushElimination = eliminationArray[randomIndex].value
            if (updatedPushElimination?.item != null) continue
            eliminationArray[randomIndex].value = null
            return true
        }
        if (eliminationArray[randomIndex].compareAndSet(pushElimination, null)) return false
        eliminationArray[randomIndex].value = null
        return true


    }


    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            if (trieberPush(x)) {
                return
            }
            for (i in (0 until 5)) {
                if (eliminationPush(x)) {
                    return
                }
            }
        }
    }

    fun trieberPop(): Pair<Boolean, E?> {
        var cur = top.value
        if (cur == null) return Pair(true, null)
        val newHead = cur.next
        if (top.compareAndSet(cur, newHead)) return Pair(true, cur.x)
        return Pair(false, null)
    }

    fun eliminationPop(): Pair<Boolean, E?> {
        var randomIndex: Int = 0
        var elimination: Elimination<E>? = null
        var result: E?
        for (i in (0 until 5)) {
            randomIndex = Random.nextInt(ELIMINATION_ARRAY_SIZE)
            elimination = eliminationArray[randomIndex].value
            if (elimination != null && elimination.isAwaitingPush()) break
        }
        if (elimination != null) {
            val updatedPushElimination = elimination.copy(item = null)
            val success = eliminationArray[randomIndex].compareAndSet(elimination, updatedPushElimination)
            result = elimination.item
            return Pair(success, result)
        }
        val popElimination = Elimination<E>(EliminationKind.POP, null)
        if (!eliminationArray[randomIndex].compareAndSet(null, popElimination)) return Pair(false, null)
        var updatedPopElimination: Elimination<E>?
        for (i in (0 until 10)) {
            updatedPopElimination = eliminationArray[randomIndex].value
            if (updatedPopElimination?.item == null) continue
            eliminationArray[randomIndex].value = null
            return Pair(true, updatedPopElimination.item)
        }
        if (eliminationArray[randomIndex].compareAndSet(popElimination, null)) return Pair(false, null)
        result = eliminationArray[randomIndex].value?.item
        eliminationArray[randomIndex].value = null
        return Pair(true, result)
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        var cur = top.value
        if (cur != null) {
            val newHead = cur.next
            if (top.compareAndSet(cur, newHead)) return cur.x
        }
        while (true) {
            for (i in (0 until 5)) {
                val eliminationResult = eliminationPop()
                if (eliminationResult.first) return eliminationResult.second
            }
            val trieber = trieberPop()
            if (trieber.first) return trieber.second
        }
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT
