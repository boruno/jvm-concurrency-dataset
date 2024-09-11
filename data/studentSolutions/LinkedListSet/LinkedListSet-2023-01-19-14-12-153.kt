package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = Int.MIN_VALUE as E, next = null)
    private val last = Node<E>(element = Int.MAX_VALUE as E, next = null)


    private val head = atomic(first)


    private fun findWindow(element: E): Window<E> {
        val window = Window<E>(null, null)
        retry@ while (true) {
            window.curr = head.value
            window.next = window.curr!!.next
            while (true) {
                if (window.next!!.element >= element) {
                    return window
                }
                val next = window.next!!.next
                if (next is Remove) {
                    if (!window.curr!!.casNext(window.next, Remove(next.element, next.next))) {
                        continue@retry
                    }
                    window.next = next
                } else {
                    window.curr = window.next
                    window.next = next
                }
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
            val window = findWindow(element)
            if (window.next!!.element == element) {
                return false
            }
            val node = Node(element, window.next)
            if (window.curr!!.casNext(window.next, node)) {
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
            if (window.next!!.element != element) {
                return false
            }
            val curNext = window.next!!.next
            if (!(curNext is Remove)) {
                if (window.next!!.casNext(curNext, Remove(curNext!!.element, curNext.next))) {
                    window.curr!!.casNext(window.next, curNext)
                    return true
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val window = findWindow(element)
        return window.next!!.element == element
    }
}

private open class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
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

private class Remove<E : Comparable<E>>(element: E?, next: Node<E>?) : Node<E>(element, next)


private class Window<E : Comparable<E>>(var curr: Node<E>?, var next: Node<E>?)