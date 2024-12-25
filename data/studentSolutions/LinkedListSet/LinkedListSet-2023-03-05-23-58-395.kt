//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private class Window<E: Comparable<E>> (
        val cur: Node<E>,
        val next: Node<E>
    )

    private fun findWindow(element: E) : Window<E> {
        while (true) {
            var cur = head.value
            var next = cur.next!!
            var flag = false;
            while (next.element < element) {
                var node = next.next
                if (node!!.status == "Removed") {
                    node = node.next
                    if (cur.casNext(next, node)) {
                        next = node!!
                    } else {
                        flag = true
                        break
                    }
                } else {
                    cur = next
                    next = node
                }
            }
            if (flag) {
                continue
            }
            while (true) {
                var node = next.next
                if (node?.status == "Removed") {
                    node = node.next
                    if (!cur.casNext(next, node)) {
                        break
                    }
                } else {
                    return Window(cur, next)
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
            if (window.cur.status == "Removed" ||
                window.next.status == "Removed" ||
                window.next.next?.status == "Removed")
                continue
            if (window.next.element == element) return false
            val node = Node(null, element, window.next)
            if (window.next.status == "Existing" &&
                window.cur.casNext(window.next, node))
                return true
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
            val node = window.next.next
            if (window.cur.status == "Removed" ||
                window.next.status == "Removed" ||
                node?.status == "Removed")
                continue
            if (window.next.element != element) {
                return false
            }
            if (window.next.casStatus("Existing", "Removed")){
                window.cur.casNext(window.next, node)
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
        return window.next.element == element
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _status = atomic("Existing")

    val status get() = _status.value

    fun casStatus(expected: String, update: String) =
        _status.compareAndSet(expected, update)

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