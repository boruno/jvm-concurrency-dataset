package mpp.linkedlistset

import kotlinx.atomicfu.atomic

class LinkedListSet<E : Comparable<E>>(f: E, s: E) {
    private val tail = Node<E>(null, null)
    private val head = Node<E>(null, tail)

    init {
        add(f)
        add(s)
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
            val (last, next) = findWindow(element)
            if (next !== tail && element == next.element) {
                return false
            }
            val cur = Node(element, next)
            if (last.casNext(next, cur)) {
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
            val (prev, nodeForRemove) = findWindow(element)
            if (nodeForRemove === tail || element != nodeForRemove.element) {
                return false
            }
            if (prev.casNext(nodeForRemove, nodeForRemove.next)) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val (_, node) = findWindow(element)
        if (node === tail) {
            return false
        }
        return element == node.element
    }

    /**
     * INV: cur.element < element <= cur.element
     */
    private fun findWindow(element: E): Window<E> {
        var cur = head
        var next = head.next!!
        while (next !== tail && next.element < element) {
            cur = next
            next = cur.next!!
        }
        return Window(cur, next)
    }
}

private data class Window<E>(val cur: Node<E>, val next: Node<E>)

private open class Node<E>(element: E?, next: Node<E>?) {
    private val _element = element
    private val _next = atomic(next)

    val element get() = _element!!
    val next get() = _next.value

    fun casNext(expected: Node<E>?, update: Node<E>?) = _next.compareAndSet(expected, update)
}