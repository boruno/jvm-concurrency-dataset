//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean { //null f(null) 1 2 3 //5// 6 l(null) null +5
        val new = Node(null, element, null)
        W1@ while (true) {
            val left = find(element)
            val right = left.next
            if (right != last && right!!.element == element) return false
            new.setNext(right)
            if (left.casNext(right, new)) {
                if (left.remove.value) continue@W1
                return true
            }

        }
    }

    private fun find(element: E): Node<E> {
        W1@ while (true) {
            var left = first
            var right = left.next
            while (right != last && right!!.element < element) {
                if (right.remove.value) {
                    if (!left.next!!.casNext(right, right.next)) {
                        continue@W1
                    }
                }
                left = right
                right = right.next
            }
            while (right!!.remove.value) {
                if (!left.next!!.casNext(right, right.next)) {
                    continue@W1
                }
                left = right
                right = right.next
            }
            return left
        }
    }

    /**
     * Removes the specified element from this set
     * if it is present.
     *
     * Returns `true` if this set contained
     * the specified element.
     */
    fun remove(element: E): Boolean {//null f(null) 6t 7 l(null) null
        while (true) {
            val left = find(element)
            val cur = left.next

            if (cur!!.remove.value) continue
            if (cur != last && cur.element != element) return false
            if (cur.remove.compareAndSet(false, true)) {

                left.casNext(cur, cur.next)
                return true
            }

        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val el = find(element).next
        return el != last && el!!.element == element
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    val remove = atomic(false)
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    // private val _prev = atomic(prev)
    //val prev get() = _prev.value
    // fun setPrev(value: Node<E>?) {
    //     _prev.value = value
    // }
    // fun casPrev(expected: Node<E>?, update: Node<E>?) =
    //     _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}