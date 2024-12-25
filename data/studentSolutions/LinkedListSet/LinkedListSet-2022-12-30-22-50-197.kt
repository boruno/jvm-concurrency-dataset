//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = Integer.MIN_VALUE as E, next = null)
    private val last = Node<E>(prev = first, element = Integer.MAX_VALUE as E, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    /**
     * Returns gap for inserting an element
     * - <current, <= element>
     */
    private fun gap(element: E) : Pair<Node<E>, Node<E>> {
        while (true) {
            var cur = first
            var next = first.next!!
            while ((!next.isAlive() || !((cur.element < element) && (element <= next.element))) && next.next != null) {
                cur = cur.next!!
                next = next.next!!
            }
            return cur to next
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
            val gap = gap(element)
            if (gap.second == element) {
                return false
            }
            val n = Node(gap.first, element, gap.second)
            if (gap.first.casNext(gap.second, n)) {
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
            val gap = gap(element)
            if (gap.second != element) {
                return false
            }
            gap.second.kill()
            gap.first.casNext(gap.second, gap.second.next)
            return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        return gap(element).second != last.element
    }

}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    val alive = atomic(true)

    fun isAlive() : Boolean { return alive.value }
    fun kill() { alive.compareAndSet(true, false) }

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }
    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}