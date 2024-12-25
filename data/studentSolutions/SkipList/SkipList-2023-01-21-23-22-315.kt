//package mpp.skiplist

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicMarkableReference

class SkipList<E : Comparable<E>>(infinityLeft: E, infinityRight: E) {
    private val head: Node<E>

    init {
        head = Node(infinityLeft, MAX_LEVEL)
        val tail = Node(infinityRight, MAX_LEVEL)
        for (i in head.next.indices) head.next[i] = AtomicMarkableReference(tail, false)
    }

    /**
     * Adds the specified [element] to this set if it is not already present.
     * Returns `true` if the [element] was not present, `false` otherwise.
     */
    fun add(element: E): Boolean {
        val topLevel = randomLevel()
        val newNode = Node(element, topLevel)
        while (true) {
            var w = findWindow(element)
            if (w.found) {
                return false
            }
            var pred = w.preds[0]!!
            var succ = w.succs[0]!!
            for (level in 0..topLevel) {
                newNode.next[level].set(w.succs[level], false)
            }

            if (!pred.next[0].compareAndSet(succ, newNode, false, false)) {
                continue
            }
            for (level in 1..topLevel) {
                while (true) {
                    pred = w.preds[level]!!
                    succ = w.succs[level]!!
                    if (pred.next[level].compareAndSet(succ, newNode, false, false)) {
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
        while (true) {
            var w = findWindow(element)
            if (!w.found) {
                return false
            }

            val nodeToRemove = w.succs[0]!!
            val removed = BooleanArray(1)

            for (level in nodeToRemove.topLevel downTo 1) {
                var succ = nodeToRemove.next[level].get(removed)
                while (!removed[0]) {
                    nodeToRemove.next[level].attemptMark(succ, true)
                    succ = nodeToRemove.next[level].get(removed)
                }
            }

            removed[0] = false
            var succ = nodeToRemove.next[0].get(removed)

            while (true) {
                val markedByMe = nodeToRemove.next[0].compareAndSet(succ, succ, false, true)
                succ = w.succs[0]!!.next[0].get(removed)
                if (markedByMe) {
                    return true
                } else if (removed[0]) {
                    return false
                }
                w = findWindow(element)
            }
        }
    }

    /**
     * Returns `true` if this set contains the specified [element].
     */
    fun contains(element: E): Boolean {
        return findWindow(element).found
    }

    /**
     * Returns the [Window], where
     * `preds[l].x < x <= succs[l].x`
     * for every level `l`
     */
    private fun findWindow(element: E): Window<E> {
        val w = Window<E>()
        val removed = BooleanArray(1)

        retry@ while (true) {
            var pred = head

            for (level in MAX_LEVEL downTo 0) {
                var curr = pred.next[level].reference
                while (true) {
                    var succ = curr.next[level].get(removed)

                    while (removed[0]) {
                        if (!pred.next[level].compareAndSet(curr, succ, false, false)) {
                            continue@retry
                        }
                        curr = pred.next[level].reference
                        succ = curr.next[level].get(removed)
                    }

                    if (curr.element < element) {
                        pred = curr
                        curr = succ
                    } else {
                        break
                    }
                }
                if (!w.found && element == curr.element) {
                    w.levelFound = level
                }
                w.preds[level] = pred
                w.succs[level] = curr
            }
            return w
        }
    }

    private open class Removed<E>(element: E, topLevel: Int) : Node<E>(element, topLevel)

    private open class Node<E>(val element: E, val topLevel: Int) {
        var next = Array<AtomicMarkableReference<Node<E>>>(topLevel + 1) {
            AtomicMarkableReference(null, false)
        }
    }

    private class Window<E> {
        var levelFound = -1 // -1 if not found
        var preds = arrayOfNulls<Node<E>?>(MAX_LEVEL + 1)
        var succs = arrayOfNulls<Node<E>?>(MAX_LEVEL + 1)
        val found get() = levelFound != -1
    }

}

private const val MAX_LEVEL = 30
private fun randomLevel(): Int = ThreadLocalRandom.current().nextInt(MAX_LEVEL)

