package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)

    init {
        first.next.value = last
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
            val current = window(element)
            if (!isGoodWindow(current)) continue
            val next = current.next.value!!
            if (next.element.value == element) return false
            val node = Node(element = element, next = next)
            if (current.casNext(next, node)) {
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
            val current = window(element)
            if (!isGoodWindow(current)) continue
            val next = current.next.value!!
            if (next.element.value != element) return false
            if (!next.casElement(element, null)) {
                return false
            }
            current.next.compareAndSet(next, next.next.value)
            return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        return window(element).next.value!!.element.value == element
    }

    private fun window(element: E): Node<E> {
        var current = first

        while (true) {

            var next = current.next.value!!
            while (next != last && next.element.value == null) {
                current.casNext(next, next.next.value!!)
                next = current.next.value!!
            }

            if (next == last) break

            val nextElement = next.element.value
            if (nextElement == null || nextElement < element) {
                current = next
            } else {
                break
            }
        }

        return current
    }

    private fun isGoodWindow(node: Node<E>): Boolean {
        if (node != first && node.empty()) {
            return false
        }
        if (node.next.value != last && node.next.value!!.empty()) {
            return false
        }
        return true
    }
}

private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    val element = atomic(element)
    val next = atomic(next)

    fun empty() = element.value == null

    fun casNext(expected: Node<E>?, update: Node<E>?) =
        next.compareAndSet(expected, update)

    fun casElement(expected: E?, update: E?) =
        element.compareAndSet(expected, update)
}