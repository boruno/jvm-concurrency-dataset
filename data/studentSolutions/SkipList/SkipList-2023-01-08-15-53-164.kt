//package mpp.skiplist

import java.lang.Math.abs
import java.util.*
import java.util.concurrent.atomic.AtomicMarkableReference

class SkipList<E : Comparable<E>> {
    private val head = Node<E>()
    private val tail = Node<E>()
    private val rand = Random()

    init {
        for (i in 0 until head.next.size) {
            head.next[i].set(tail, false)
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
        val topLevel = randomLevel()
        val bottomLevel = 0
        val preds = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        val succs = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        while (true) {
            val found = find(element, preds, succs)
            if (found) {
                return false
            } else {
                val newNode = Node(element, topLevel)
                for (level in bottomLevel..topLevel) {
                    val succ = succs[level]
                    newNode.next[level].set(succ, false)
                }
                var pred = preds[bottomLevel]!!
                var succ = succs[bottomLevel]
                if (!pred.next[bottomLevel].compareAndSet(
                        succ, newNode, false, false
                    )
                ) {
                    continue
                }
                for (level in bottomLevel + 1..topLevel) {
                    while (true) {
                        pred = preds[level]!!
                        succ = succs[level]
                        if (pred.next[level].compareAndSet(succ, newNode, false, false)) break
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
        val preds = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        val succs = arrayOfNulls<Node<E>>(MAX_LEVEL + 1)
        var succ: Node<E>?
        while (true) {
            val found = find(element, preds, succs)
            if (!found) {
                return false
            } else {
                val nodeToRemove = succs[bottomLevel]
                for (level in nodeToRemove!!.topLevel downTo bottomLevel + 1) {
                    val marked = booleanArrayOf(false)
                    succ = nodeToRemove.next[level].get(marked)
                    while (!marked[0]) {
                        nodeToRemove.next[level].compareAndSet(succ, succ, false, true)
                        succ = nodeToRemove.next[level].get(marked)
                    }
                }
                val marked = booleanArrayOf(false)
                succ = nodeToRemove.next[bottomLevel].get(marked)
                while (true) {
                    val iMarkedIt = nodeToRemove.next[bottomLevel].compareAndSet(
                        succ, succ,
                        false, true
                    )
                    succ = succs[bottomLevel]!!.next[bottomLevel].get(marked)
                    if (iMarkedIt) {
                        find(element, preds, succs)
                        return true
                    } else if (marked[0]) return false
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val marked = booleanArrayOf(false)
        val bottomLevel = 0
        var pred = head
        var curr: Node<E>? = null
        var succ: Node<E>?
        for (level in MAX_LEVEL downTo bottomLevel) {
            curr = pred.next[level].reference
            while (true) {
                succ = curr!!.next[level].get(marked)
                while (marked[0]) {
                    curr = pred.next[level].reference
                    succ = curr.next[level].get(marked)
                }
                if (curr!!.key!! < element) {
                    pred = curr
                    curr = succ
                } else {
                    break
                }
            }
        }
        return curr!!.key == element
    }

    private fun find(key: E, preds: Array<Node<E>?>, succs: Array<Node<E>?>): Boolean {
        val bottomLevel = 0
        val marked = booleanArrayOf(false)
        var snip: Boolean
        var pred: Node<E>?
        var curr: Node<E>? = null
        var succ: Node<E>?
        retry@ while (true) {
            pred = head
            for (level in MAX_LEVEL downTo bottomLevel) {
                curr = pred!!.next[level].reference
                while (true) {
                    succ = curr!!.next[level][marked]
                    while (marked[0]) {
                        snip = pred!!.next[level].compareAndSet(
                            curr, succ,
                            false, false
                        )
                        if (!snip) continue@retry
                        curr = pred.next[level].reference
                        succ = curr.next[level][marked]
                    }
                    if (curr !== tail && curr!!.key!! < key) {
                        pred = curr
                        curr = succ
                    } else {
                        break
                    }
                }
                preds[level] = pred
                succs[level] = curr
            }
            return curr!!.key == key
        }
    }

    private fun randomLevel(): Int {
        var rint = rand.nextInt()
        if (rint == Integer.MIN_VALUE) {
            return 31
        }
        if (rint == 0) {
            return 30
        }
        if (abs(rint) <= 3 * (1 shl 29)) {
            return 0
        }
        rint += if (rint < 0) 3 * (1 shl 29) else -3 * (1 shl 29)
        for (level in 1..28) {
            if (rint <= 1 shl (29 - level)) {
                return level
            }
            rint += if (rint < 0) 1 shl (29 - level) else -(1 shl (29 - level))
        }
        return 29
    }
}

private const val MAX_LEVEL = 31

private class Node<T : Comparable<T>>(
    val key: T?,
    val next: Array<AtomicMarkableReference<Node<T>>>,
    val topLevel: Int
) {
    constructor() : this(null, Array(MAX_LEVEL + 1) { AtomicMarkableReference(null, false) }, MAX_LEVEL)
    constructor(x: T, height: Int) : this(x, Array(height + 1) { AtomicMarkableReference(null, false) }, height)
}