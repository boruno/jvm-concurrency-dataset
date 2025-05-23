//package mpp.skiplist

import java.util.concurrent.atomic.AtomicMarkableReference
import kotlin.random.Random



class SkipList<E : Comparable<E>> {

    fun AtomicMarkableReference<Node<E>>.get() : Pair<Node<E>, Boolean> {
        val marked = BooleanArray(1) { false }
        val v = get(marked)
        return v to marked[0]
    }

    private val rnd = Random(300)

    private val BOTTOM_LEVEL = 0
    private val MAX_LEVEL = 8
    private val tail = Node(Int.MAX_VALUE as E)
    private val head = Node(Int.MIN_VALUE as E, tail)

    private fun find(key: E, prevs: Array<Node<E>?>, nexts: Array<Node<E>?>): Boolean {
        retry@ while (true) {

            var prev: Node<E> = head
            var curr: Node<E> = head
            var next: Pair<Node<E>, Boolean> = Pair(tail, false)
            for (level in MAX_LEVEL downTo BOTTOM_LEVEL) {
                do {
                    if (next.second) {
                        if (!prev.next[level].compareAndSet(curr, next.first, false, false)) {
                            continue@retry
                        }
                        curr = next.first
                    } else {
                        curr = prev.next[level].reference ?: tail
                    }
                    prev = curr
                    next = curr.next[level].get()
                } while (curr.key < key)
            }
            return curr.key == key


//            var prev: Node<E> = head
//            var curr: Node<E> = head
//            var next: Pair<Node<E>, Boolean>
//            for (level in MAX_LEVEL downTo BOTTOM_LEVEL) {
//                curr = prev.next[level].reference
//                while (true) {
//                    next = curr.next[level].get()
//                    while (next.second) {
//                        if (!prev.next[level].compareAndSet(curr, next.first, false, false)) {
//                            continue@retry
//                        }
//                        curr = prev.next[level].reference
//                        next = curr.next[level].get()
//                    }
//                    if (curr.key < key) {
//                        prev = curr
//                        curr = next.first
//                    } else {
//                        break
//                    }
//                }
//                prevs[level] = prev
//                nexts[level] = curr
//            }
//            return curr.key == key

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

        val topLevel = rnd.nextInt(MAX_LEVEL)
        val newNode = Node(element, topLevel)

        val prevs = Array<Node<E>?>(MAX_LEVEL + 1) { null }
        val nexts = Array<Node<E>?>(MAX_LEVEL + 1) { null }

        do {
            if (find(element, prevs, nexts)) {
                return false
            }
            for (level in BOTTOM_LEVEL..topLevel) {
                newNode.next[level] = AtomicMarkableReference(nexts[level], false)
            }
        } while (!prevs[BOTTOM_LEVEL]!!.next[BOTTOM_LEVEL]
                    .compareAndSet(nexts[BOTTOM_LEVEL]!!, newNode,false,false)
        )

        for (level in BOTTOM_LEVEL + 1..topLevel) {
            var prev = prevs[level]!!
            var next = nexts[level]!!
            while (!prev.next[level].compareAndSet(next, newNode, false, false)) {
                find(element, prevs, nexts)
                prev = prevs[level]!!
                next = nexts[level]!!
            }
        }

        return true
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {
        val preds = Array<Node<E>?>(MAX_LEVEL + 1) { null }
        val succs = Array<Node<E>?>(MAX_LEVEL + 1) { null }

        if (!find(element, preds, succs)) {
            return false
        } else {
            val nodeToRemove = succs[BOTTOM_LEVEL]!!
            for (level in nodeToRemove.topLevel downTo BOTTOM_LEVEL + 1) {
                do {
                    val p = nodeToRemove.next[level].get()
                } while (!p.second && ! nodeToRemove.next[level].attemptMark(p.first, true))
            }

            var p = nodeToRemove.next[BOTTOM_LEVEL].get()
            while (!nodeToRemove.next[BOTTOM_LEVEL].compareAndSet(p.first, p.first, false, true)) {
                p = nodeToRemove.next[BOTTOM_LEVEL].get()
                if (p.second) {
                    return false
                }
            }

            find(element, preds, succs)
            return true

        }

    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var prev: Node<E> = head
        var curr: Node<E> = head
        var next: Pair<Node<E>, Boolean> = Pair(tail, false)
        for (level in MAX_LEVEL downTo BOTTOM_LEVEL) {
            do {
                if (next.second) {
                    curr = next.first
                } else {
                    curr = prev.next[level].reference ?: tail
                }
                prev = curr
                next = curr.next[level].get()
            } while (curr.key < element)
        }
        return curr.key == element
    }

    inner class Node<E : Comparable<E>>(
        val key: E,
        val next: Array<AtomicMarkableReference<Node<E>>>,
        val topLevel: Int
    ) {

        constructor(key: E) : this(key, null)

        constructor(key: E, tail: Node<E>?) : this(
            key,
            Array<AtomicMarkableReference<Node<E>>>(1 + MAX_LEVEL) {
                AtomicMarkableReference(tail, false)
            },
            MAX_LEVEL
        )

        constructor(key: E, height: Int) : this(
            key,
            Array<AtomicMarkableReference<Node<E>>>(1 + height) {
                AtomicMarkableReference(null, false)
            },
            height
        )

    }

}


