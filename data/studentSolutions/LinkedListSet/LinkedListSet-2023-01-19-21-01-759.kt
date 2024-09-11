package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)
    init {
        first.setNext(last, false)
    }

    class Pair<E>(var first: E, var second: E)

    private val head = atomic(first)

    private fun getPosition(element: E): Pair<Node<E>?> {
        while (true) {
            var cur = first
            var next: Node<E>? = first.next
            while (next != last && (next!!.isRemoved() || next!!.element < element)) {
                if (cur.isRemoved()) {
                    break
                }
                if (next!!.isRemoved()) {
                    val newNext = next.next!!.next//?
                    next = if (cur.casNext(next, true, newNext, false)) {
                        newNext
                    } else {
                        cur.next
                    }
                } else {
                    cur = next!!
                    next = cur.next
                }
            }

            if (!cur.isRemoved()) {
                return Pair(cur, next)
            }
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
        while (true) {
            val pos = getPosition(element)
            if (pos.second == last || pos.second!!.element == element) {
                return false
            }
            if (pos.first!!.casNext(pos.second, pos.second!!.isRemoved(), Node(element, pos.second), false)) {
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
            val pos = getPosition(((element as Int) + 1) as E)
            if (pos.first!!.element != element) {
                return false
            }
            if (pos.first!!._nextAndRemoved.attemptMark(pos.second, true)) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val pos = getPosition(element)
        return pos.second != last && pos.second!!.element == element
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    public val _nextAndRemoved = AtomicMarkableReference(next, false)
    val next get() = _nextAndRemoved.reference
    fun setNext(next: Node<E>?, removed: Boolean) {
        _nextAndRemoved.set(next, removed)
    }
    fun casNext(nextExpect: Node<E>?, removedExpect: Boolean, nextUpdate: Node<E>?, removedUpdate: Boolean) =
        _nextAndRemoved.compareAndSet(nextExpect, nextUpdate, removedExpect, removedUpdate)

    fun isRemoved(): Boolean {
        return _nextAndRemoved.isMarked
    }
}