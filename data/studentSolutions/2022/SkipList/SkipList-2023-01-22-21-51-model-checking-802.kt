package mpp.skiplist

import java.util.concurrent.atomic.AtomicMarkableReference
import kotlin.random.Random

class SkipList<E : Comparable<E>> {
    val head = Node<E>(Int.MIN_VALUE)
    val tail = Node<E>(Int.MAX_VALUE)

    val random = Random(222)

    init {
        for (i in 0 until head.next!!.size) {
            head.next!![i] = AtomicMarkableReference<Node<E>?>(tail, false)
        }
    }

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        val topLevel = random.nextInt(0, MAX_LEVEL)
        val bottomLevel = 0
        val preds = Array<Node<E>?>(MAX_LEVEL + 1) { null }
        val succs = Array<Node<E>?>(MAX_LEVEL + 1) { null }
        while (true) {
            val found = find(element, preds, succs)
            if (found) {
                return false
            } else {
                val newNode = Node<E>(element, topLevel)
                for (level in bottomLevel..topLevel) {
                    val succ = succs[level]
                    newNode.next!![level].set(succ, false)
                }
                var pred = preds[bottomLevel]
                var succ = succs[bottomLevel]
                newNode.next!![bottomLevel].set(succ, false)
                if (!pred!!.next!![bottomLevel].compareAndSet(succ, newNode, false, false)){
                    continue
                }
                for (level in (bottomLevel + 1)..topLevel) {
                    while (true) {
                        pred = preds[level]
                        succ = succs[level]
                        if (pred!!.next!![level].compareAndSet(succ, newNode, false, false)) {
                            break
                        }
                        find(element, preds, succs)
                    }
                }
                return true
            }
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        val bottomLevel = 0
        val preds = Array<Node<E>?>(MAX_LEVEL + 1) { null }
        val succs = Array<Node<E>?>(MAX_LEVEL + 1) { null }
        var succ: Node<E>? = null
        while (true) {
            val found = find(element, preds, succs)
            if (!found) {
                return false
            } else {
                val nodeToRemove = succs[bottomLevel]
                for (level in nodeToRemove!!.topLevel.downTo(bottomLevel + 1)) {
                    var marked = BooleanArray(1) { false }
                    succ = nodeToRemove.next!![level].get(marked)
                    while (!marked[0]) {
                        nodeToRemove.next!![level].attemptMark(succ, true)
                        succ = nodeToRemove.next!![level].get(marked)
                    }
                }
                var marked = BooleanArray(1) { false }
                succ = nodeToRemove.next!![bottomLevel].get(marked)
                while (true) {
                    var iMarkedIt = nodeToRemove.next!![bottomLevel].compareAndSet(succ, succ, false, true)
                    succ = succs[bottomLevel]!!.next!![bottomLevel].get(marked)
                    if (iMarkedIt) {
                        find(element, preds, succs)
                        return true
                    }
                    else if (marked[0]) {
                        return false
                    }
                }
            }
        }
    }

    fun find(element: E, preds: Array<Node<E>?>, succs: Array<Node<E>?>): Boolean {
        val bottomLevel = 0
        val key = element.hashCode()
        var marked = BooleanArray(1) { false }
        var snip: Boolean

        var pred: Node<E>? = null
        var curr: Node<E>? = null
        var succ: Node<E>? = null

        while (true) {
            pred = head
            for (level in MAX_LEVEL.downTo(bottomLevel)) {
                curr = pred!!.next!![level].reference
                while (true) {
                    succ = curr!!.next!![level].get(marked)
                    while (marked[0]) {
                        snip = pred!!.next!![level].compareAndSet(curr, succ, false, false)
                        if (!snip) continue
                        curr = pred.next!![level].reference
                        succ = curr!!.next!![level].get(marked)
                    }
                    if (curr!!.key < key) {
                        pred = curr
                        curr = succ
                    } else {
                        break
                    }
                }
                preds[level] = pred!!
                succs[level] = curr!!
            }
            return (curr!!.key == key)
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val bottomLevel = 0
        val v = element.hashCode()
        var marked = BooleanArray(1) { false }
        var pred = head
        var curr: Node<E>? = null
        var succ: Node<E>? = null
        for (level in MAX_LEVEL.downTo(bottomLevel)) {
            curr = pred.next!![level].reference
            while (true) {
                succ = curr!!.next!![level].get(marked)
                while (marked[0]) {
                    curr = pred.next!![level].reference
                    succ = curr!!.next!![level].get(marked)
                }
                if (curr!!.key < v) {
                    pred = curr
                    curr = succ
                } else {
                    break
                }
            }
        }
        return (curr!!.key == v)
    }

    class Node<E> {
        var value: E? = null
        var key: Int = 0
        var next: Array<AtomicMarkableReference<Node<E>?>>? = null
        var topLevel: Int = 0

        constructor(initKey: Int) {
            value = null
            key = initKey
            next = Array(MAX_LEVEL + 1) { AtomicMarkableReference<Node<E>?>(null, false) }
            topLevel = MAX_LEVEL
        }

        constructor(initValue: E, initHeight: Int) {
            value = initValue
            key = value.hashCode()
            next = Array(MAX_LEVEL + 1) { AtomicMarkableReference<Node<E>?>(null, false) }
            topLevel = initHeight
        }
    }
}

private const val MAX_LEVEL = 8 // DO NOT CHANGE ME