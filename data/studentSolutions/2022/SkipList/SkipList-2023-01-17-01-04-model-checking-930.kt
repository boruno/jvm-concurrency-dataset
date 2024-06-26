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
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class SkipList<E : Comparable<E>> {
    private val head: Node<E>

    init {
        head = Node(infinityLeft as E, MAX_LEVEL)
        val tail = Node(infinityRight, MAX_LEVEL)
        for (i in head.next.indices) head.next[i].set(tail)
    }

    /**
     * Adds the specified [element] to this set if it is not already present.
     * Returns `true` if the [element] was not present, `false` otherwise.
     */
    fun add(element: E): Boolean {
        val topLevel = randomLevel()
        val bottomLevel = 0
        while (true) {
            var w = findWindow(element)
            if (w.found) {
                return false
            }
            val newNode = Node(element, topLevel)
            for (level in bottomLevel..topLevel) {
                val succ = value(w.succs[level])
                next(newNode, level).set(succ)
            }
            var pred = w.preds[bottomLevel]
            var succ = w.succs[bottomLevel]
            if (succ is Removed<*> || !next(pred, bottomLevel).compareAndSet(succ, newNode)) {
                continue
            }
            for (level in bottomLevel + 1..topLevel) {
                while (true) {
                    pred = w.preds[level]
                    succ = w.succs[level]
                    if (succ !is Removed<*> && next(pred, level).compareAndSet(succ, newNode)) {
                        break
                    }
                    w = findWindow(element)
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
        val bottomLevel = 0
        var succ: Any?
        while (true) {
            val w = findWindow(element)
            if (!w.found) {
                return false
            }
            val nodeToRemove = value(w.succs[bottomLevel])
            for (level in nodeToRemove.topLevel downTo bottomLevel + 1) {
                succ = next(nodeToRemove, level).get()
                while (succ !is Removed<*>) {
                    next(nodeToRemove, level).compareAndSet(succ, Removed(succ as Node<*>))
                    succ = next(nodeToRemove, level).get()
                }
            }
            succ = next(nodeToRemove, bottomLevel).get()
            while (true) {
                val marked = succ !is Removed<*> &&
                        next(nodeToRemove, bottomLevel).compareAndSet(succ, Removed(succ as Node<*>))
                succ = next(nodeToRemove, bottomLevel).get()
                if (marked) {
                    findWindow(element)
                    return true
                } else if (succ is Removed<*>) {
                    return false
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains the specified [element].
     */
    fun contains(element: E): Boolean {
        val bottomLevel = 0
        var pred: Any? = head
        var curr: Any? = null
        var succ: Any?
        for (level in MAX_LEVEL downTo bottomLevel) {
            curr = next(pred, level).get()
            while (true) {
                succ = next(curr, level).get()
                while (succ is Removed<*>) {
                    curr = next(curr, level).get()
                    succ = next(curr, level).get()
                }
                if (value(curr).element < element) {
                    pred = curr
                    curr = succ
                } else {
                    break
                }
            }
        }
        return value(curr).element.compareTo(element) == 0
    }

    /**
     * Returns the [Window], where
     * `preds[l].x < x <= succs[l].x`
     * for every level `l`
     */
    private fun findWindow(element: E): Window {
        val w = Window()
        val bottomLevel = 0
        var snip: Boolean
        var pred: Any?
        var curr: Any? = null
        var succ: Any?
        repeat@ while (true) {
            pred = head
            for (level in MAX_LEVEL downTo bottomLevel) {
                curr = next(pred, level).get()
                while (true) {
                    succ = next(curr, level).get()
                    while (succ is Removed<*>) {
                        snip = curr !is Removed<*> && next(pred, level).compareAndSet(curr, succ.node)
                        if (!snip) continue@repeat
                        curr = next(pred, level).get()
                        succ = next(curr, level).get()
                    }
                    if (value(curr).element < element) {
                        pred = curr
                        curr = succ
                    } else {
                        break
                    }
                }
                w.preds[level] = pred
                w.succs[level] = curr
            }
            w.found = value(curr).element.compareTo(element) == 0
            return w
        }
    }

    private fun next(node: Any?, level: Int): AtomicReference<Any?> = value(node).next[level]

    private fun value(node: Any?): Node<E> =
        if (node is Removed<*>) node.node as Node<E> else node as Node<E>
}

private class Node<E>(
    val element: E,
    val topLevel: Int
) {
    val next = Array<AtomicReference<Any?>>(topLevel + 1) {
        AtomicReference(null)
    }
}

private class Removed<E>(val node: Node<E>)

private class Window {
    var found = false
    var preds = arrayOfNulls<Any?>(MAX_LEVEL + 1)
    var succs = arrayOfNulls<Any?>(MAX_LEVEL + 1)
}

private fun randomLevel(): Int = Random.nextInt(MAX_LEVEL)

private const val MAX_LEVEL = 30
private val infinityLeft: Any = Int.MIN_VALUE
private val infinityRight: Any = Int.MAX_VALUE