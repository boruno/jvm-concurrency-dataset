//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.awt.Window




class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private fun findWindow(element: E): Pair<Node<E>, Node<E>> {
        while (true) {
            var cur = head.value
            var next = cur.next
            while (next?.element!! < element || next.isDeleted) {
                if (cur.isDeleted) break
                if (next.isDeleted) {
                    val nextNext = next.next?.next
                    if (!cur.casNext(next, nextNext)) {
                        next = cur.next
                        continue
                    }
                } else {
                    cur = next
                    next = cur.next
                }
            }
            if (cur.isDeleted) continue
            return Pair(cur, next)
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
//        TODO("implement me")
        while (true) {
            val window = findWindow(element)
            if (window.second.element == element)
                return false
            val newNode = Node(window.first, element, window.second)
            if (window.first.casNext(window.second, newNode) && window.second.casPrev(window.first, newNode))
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
//        TODO("implement me")
        if (!contains(element))
            return false
        while (true) {
            val window = findWindow(element)
            if (window.second.element != element)
                return false
            if (window.first.casNext(window.second.next, window.second.next) && window.second.next?.casPrev(window.second, window.first) == true)
                return true
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
//        TODO("implement me")
        val window = findWindow(element)
        return window.second.element == element
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    var isDeleted = false
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