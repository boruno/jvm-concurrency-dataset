package mpp.skiplist
/*
class SkipList<E : Comparable<E>> {

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        TODO("implement me")
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        TODO("implement me")
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        TODO("implement me")
    }
}
*/

import java.util.concurrent.atomic.AtomicMarkableReference
import kotlin.random.Random

class SkipList<E : Comparable<E>> {
    private val head: Node<E> = Node(infinityLeft as E, MAX_LEVEL)

    init {
        val tail = Node(infinityRight as E, MAX_LEVEL)
        for (i in head.next.indices) {
            head.next[i] = AtomicMarkableReference(tail, false)
        }
    }

    /**
     * Adds the specified [element] to this set if it is not already present.
     * Returns `true` if the [element] was not present, `false` otherwise.
     */
    fun add(element: E): Boolean {
        val topLevel = randomLevel()
        while (true) {
            var window: Window<E> = findWindow(element)
            if (window.found) {
                return false
            }
            val newNode = Node(element, topLevel)
            for (level in 0..topLevel) {
                val succ = window.succs[level]
                newNode.next[level].set(succ, false)
            }
            var pred = window.preds[0]
            var succ = window.succs[0]
            newNode.next[0].set(succ, false)
            if (!pred!!.next[0].compareAndSet(succ, newNode, false, false)) {
                continue
            }
            for (level in 1..topLevel) {
                while (true) {
                    pred = window.preds[level]
                    succ = window.succs[level]
                    if (pred!!.next[level].compareAndSet(succ, newNode, false, false)) {
                        break
                    }
                    window = findWindow(element)
                }
            }
            return true
        }
    }

    /**
     * Removes the specified [element] from this set.
     * Returns `true` if the [element] was presented in this set,
     * `false` otherwise.
     */
    fun remove(element: E): Boolean {
        while (true) {
            val window = findWindow(element)
            if (!window.found) {
                return false
            } else {
                var succ: Node<E>
                val nodeToRemove = window.succs[0]
                for (level in nodeToRemove!!.topLevel downTo 1) {
                    val marked: BooleanArray = booleanArrayOf(false)
                    succ = nodeToRemove.next[level].get(marked)
                    while (!marked[0]) {
                        nodeToRemove.next[level].attemptMark(succ, true)
                        succ = nodeToRemove.next[level].get(marked)
                    }
                }
                val marked: BooleanArray = booleanArrayOf(false)
                succ = nodeToRemove.next[0].get(marked)
                while (true) {
                    val iMarkedIt = nodeToRemove.next[0].compareAndSet(succ, succ, false, true)
                    succ = window.succs[0]!!.next[0].get(marked)
                    if (iMarkedIt) {
                        findWindow(element)
                        return true
                    } else if (marked[0]) {
                        return false
                    }
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains the specified [element].
     */
    fun contains(element: E): Boolean {
        val w = findWindow(element)
        return w.found
    }

    /**
     * Returns the [Window], where
     * `preds[l].x < x <= succs[l].x`
     * for every level `l`
     */
    private fun findWindow(element: E): Window<E> {
        retry@ while (true) {
            val w = Window<E>()
            val marked: BooleanArray = booleanArrayOf(false)
            var pred = head
            var curr = head
            for (level in MAX_LEVEL downTo 0) {
                curr = pred.next[level].reference
                while (true) {
                    var succ = curr.next[level].get(marked)
                    while (marked[0]) {
                        val snip = pred.next[level].compareAndSet(curr, succ, false, false)
                        if (!snip) {
                            continue@retry
                        }
                        curr = pred.next[level].reference
                        succ = curr.next[level].get(marked)
                    }
                    if (curr.element < element) {
                        pred = curr
                        curr = succ
                    } else {
                        break
                    }
                }
                w.preds[level] = pred
                w.succs[level] = curr
            }
            w.found = curr.element == element
            return w
        }
    }
}

private class Node<E>(
    val element: E,
    val topLevel: Int
) {
    val next: Array<AtomicMarkableReference<Node<E>>> = Array(topLevel + 1) {
        AtomicMarkableReference(null, false)
    }
}

private class Window<E> {
    var preds = Array<Node<E>?>(MAX_LEVEL + 1) { null }
    var succs = Array<Node<E>?>(MAX_LEVEL + 1) { null }

    var found = false
}

private fun randomLevel(): Int = Random.nextInt(MAX_LEVEL)

private const val MAX_LEVEL = 30
private val infinityLeft: Any = Int.MIN_VALUE
private val infinityRight: Any = Int.MAX_VALUE