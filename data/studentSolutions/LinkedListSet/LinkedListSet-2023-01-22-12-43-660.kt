//package mpp.linkedlistset

import kotlinx.atomicfu.*
import kotlin.random.Random

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node(prev = first, element = null, next = null)

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
//        TODO("implement me")
        while (true) {
            val window = find(element)
//            val node: Node<E>? = window.next!!.next
            if (window.cur!!.isRemoved() || window.next!!.isRemoved() || window.next.next!!.isRemoved()) {
                continue
            }
            if (window.next.element == element) {
                return false
            }
            val node = Node(window.cur, element, window.next)
            if (!window.next.isRemoved() && window.cur.casNext(window.next, node)) {
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
//        TODO("implement me")
        while (true) {
            val window = find(element)
            val node: Node<E>? = window.next!!.next
            if (window.cur!!.isRemoved() || window.next.isRemoved() || node!!.isRemoved()) {
                continue
            }
            if (window.next.element != element) {
                return false
            }
            val newNode = Node(window.next.prev, window.next.element, window.next.next)
            if (window.next.casNext(node, newNode)) {
                window.cur.casNext(window.next,node)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
//        TODO("implement me")
        val window = find(element)
        return window.next!!.element == element
    }

    private fun find(key: E): Window<E> {
        while (true) {
            var curr: Node<E>? = head.value
            var next: Node<E>? = curr?.next
            var flag = false
            retry@ while (next!!.element >= key) {
                var node: Node<E>? = next.next
                if (node!!.isRemoved()) {
                    node = node.next
                    if (curr!!.casNext(next, node)) {
                        next = node
                    } else {
                        flag = true
                        break@retry
                    }
                } else {
                    curr = next
                    next = node
                }
            }
            if (flag) {
                continue
            }
            while (true) {
                var node = next.next
                if (node!!.isRemoved()) {
                    node = node.next
                    if (!curr!!.casNext(next, node)) {
                        break
                    }
                } else {
                    return Window(curr, next)
                }
            }

        }
    }
}

private class Window<E : Comparable<E>>(val cur: Node<E>?, val next: Node<E>?)


private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _prev = atomic(prev)
    val prev get() = _prev.value

    val removed = atomic(false)

    fun setRemoved() {
        removed.value = true
    }

    fun isRemoved() = removed.value
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