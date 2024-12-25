//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val last = Node<E>(element = null, next = null)
    private val first = Node<E>(element = null, next = null)
    init {
        first.setNext(last)
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
            val window: Window<E> = findWindow(element)

            if (window.next !== last && element == window.next.element) {
                return false
            }

            if (window.cur.casNext(window.next, Node(element, window.next))) {
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
            val node = findWindow(element).next
            if (node === last || element != node.element) {
                return false
            }
            if (node.casNext(node, Remove(node.element, node.next))) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val found = findWindow(element).next.element
        return element == found
    }

    private fun findWindow(element: E): Window<E> {
        var cur = first
        var next = first.next!!
        while (next !== last && next.element < element) {
            when (val nextNext = next.next) {
                is Remove -> {
                    cur.casNext(next, nextNext)
                    next = nextNext
                } else -> {
                    cur = next
                    next = cur.next!!
                }
            }
        }
        return Window(cur, next)
    }
}

private open class Node<E>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class Window<E>(val cur: Node<E>, val next: Node<E>)
private class Remove<E>(element: E?, next: Node<E>?) : Node<E>(element, next)