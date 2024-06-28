package mpp.linkedlistset

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
    private fun find(head: Node<E>, key: E): Pair<Node<E>, Node<E>> {
        var pred : Node<E>
        var curr : Node<E>
        var succ : Node<E>?

        val marked = booleanArrayOf(false)
        var snip: Boolean

        retry@ while (true) {
            pred = head
            curr = pred.next!!

            while (true) {
                succ = curr._next.get(marked)
                while (marked[0]) {
                    snip = pred.casNext(curr, succ, false, false)
                    if (!snip) {
                        continue@retry
                    }

                    curr = succ!!
                    succ = curr._next.get(marked)
                }

                if (curr == last) { // reached end
                    return Pair(pred, curr)
                }
                if (curr.element >= key) {
                    return Pair(pred, curr)
                }
                pred = curr
                curr = succ!!
            }
        }
    }


    fun add(element: E): Boolean {
        while (true) {
            val window = find(head.value, element)
            val pred = window.first
            val curr = window.second

            if (curr != last && curr.element == element) {
                return false
            } else {
                val node = Node(element, curr)
                if (pred._next.compareAndSet(curr, node, false, false)) {
                    return true
                }
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
        var snip: Boolean
        while (true) {
            val window = find(head.value, element)
            val pred = window.first
            val curr = window.second
            if (curr.element == element) {
                return false
            } else {
                val succ = curr.next
                snip = curr._next.compareAndSet(succ, succ, false, true)
                if (!snip) {
                    continue
                }
                pred._next.compareAndSet(curr, succ, false, false)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var curr = head.value
        while (curr.element < element) {
            curr = curr.next!!
        }
        return (curr.element == element && !curr._next.isMarked)
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    val _next = AtomicMarkableReference(next, false)
    val next get() = _next.reference
    fun setNext(value: Node<E>?, mark: Boolean) {
        _next.set(value, mark)
    }

    fun casNext(expected: Node<E>?, update: Node<E>?, oldMark: Boolean, newMark: Boolean) =
        _next.compareAndSet(expected, update, oldMark, newMark)
}