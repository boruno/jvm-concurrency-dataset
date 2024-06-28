package mpp.linkedlistset

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
    fun add(element: E): Boolean {
        while (true) {
            if (contains(element)) {
                return false
            }
            val window = findWindow(element)
            val node = Node(null, element, window.right)
            if (window.left.casNext(window.right, node)) {
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
            val window = findWindow(element)
            if (window.right == last || window.right.element != element) {
                return false
            }
            val leftNext = window.left.next
            if (leftNext is Marked) {
                continue
            }
            val rightNext = window.right.next
            if (rightNext is Marked) {
                continue
            }
            if (!window.right.casNext(rightNext, Marked(rightNext!!))) {
                continue
            }
            if (window.left.casNext(leftNext, rightNext)) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val window = findWindow(element)
        if (window.right == last) {
            return false
        }
        return element == window.right.element
    }

    private fun findWindow(element: E): Window<E> {
        var cur = first
        while (true) {
            if (cur == first || cur.element < element) {
                if (cur.next == last || element <= cur.next!!.element) {
                    return Window(cur, cur.next!!)
                }
            }
            cur = cur.next!!
        }
    }
}

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

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

private class Window<E : Comparable<E>>(val left: Node<E>, val right: Node<E>)

private class Marked<E : Comparable<E>>(node: Node<E>) : Node<E>(node.prev, node.element, node.next)