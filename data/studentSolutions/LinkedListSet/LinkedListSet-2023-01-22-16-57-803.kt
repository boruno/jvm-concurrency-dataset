//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)

    init {
        first.setNext(last, false)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val (lower, notLower) = find(element)
            val new = Node(element, notLower)
            if (notLower.element == element) {
                return false
            }
            if (lower.casNext(notLower, new, false, false)) {
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
        while (true) {
            val (lower, notLower) = find(element)
            if (notLower.element != element) {
                return false
            }
            val nextNew = notLower.next!!
            if (notLower.casNext(nextNew, nextNew, false, true)) {
                lower.casNext(notLower, nextNew, false, false)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        return find(element).second == element
    }

    private fun find(element: E): Pair<Node<E>, Node<E>> {
        Loop@ while (true) {
            var cur = head.value
            var next = head.value.next
            while (next?.element != null && next.element < element) {
                val (nextNew, nextNewMark) = next.nextPair
                if (nextNewMark) {
                    if (cur.casNext(next, nextNew, false, false)) {
                        next = nextNew
                    } else {
                        continue@Loop
                    }
                } else {
                    cur = next
                    next = cur.next
                }
            }
            val (nextNew, nextNewMark) = next!!.nextPair
            if (nextNewMark) {
                cur.casNext(next, nextNew, false, false)
            } else {
                return Pair(cur, next)
            }
        }
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = AtomicMarkableReference(next, false)

    val next
        get() = _next.reference

    val nextPair: Pair<Node<E>, Boolean>
        get() {
            val mark = BooleanArray(1)
            val ref = _next.get(mark)!!
            return Pair(ref, mark[0])
        }

    fun casNext(expected: Node<E>, update: Node<E>, expectedMark: Boolean, updateMark: Boolean): Boolean {
        return _next.compareAndSet(expected, update, expectedMark, updateMark)
    }

    fun setNext(update: Node<E>, mark: Boolean) {
        _next.set(update, mark)
    }

}