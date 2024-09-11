package mpp.linkedlistset

import kotlinx.atomicfu.atomic

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)

    init {
        first.setNext(last)
    }

    private val head = atomic(first)
    private val tail = atomic(last)

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
            if (next != tail.value && element == next.element) {
                return false
            }
            val cur = Node(null, element, next)
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
            val (_, nodeForRemove) = findWindow(element)
            if (nodeForRemove == tail.value || element != nodeForRemove.element) {
                return false
            }
            val removed = Node(null, nodeForRemove.element, nodeForRemove.next)
            removed.isActive.getAndSet(false)
            if (nodeForRemove.casNext(
                    nodeForRemove, removed
                )
            ) {
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
        if (node == tail.value) {
            return false
        }
        return element == node.element
    }

    /**
     * INV: cur.element < element <= cur.element
     */
    private fun findWindow(element: E): Window<E> {
        /*var cur = head.value
        var next = head.value.next!!
        while (next != tail.value && next.element < element) {
            val nodeAfterWindow = next.next ?: break
            if (!nodeAfterWindow.isActive.value) {
                if (!cur.casNext(next, Node(null, nodeAfterWindow.element, nodeAfterWindow.next))) {
                    return findWindow(element)
                }
                next = nodeAfterWindow
            } else {
                cur = next
                next = cur.next!!
            }
        }
        return Window(cur, next)*/
        while (true) {
            var cur = head.value
            var next = cur.next!!
            var flag = false
            while (next.element < element) {
                val node = next.next ?: break
                if (node.isActive.value) {
                    cur = next
                    next = node
                } else {
                    //node.isActive.getAndSet(false)
                    val newNode = Node(null, node.element, node.next)
                    if (cur._next.compareAndSet(next, newNode)) next = node else {
                        flag = true
                        break
                    }
                }
            }
            if (flag) continue
            return Window(cur, next)
        }
    }
}

private data class Window<E : Comparable<E>>(val cur: Node<E>, val next: Node<E>)

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element
    val _next = atomic(next)
    val isActive = atomic(true)

    val element get() = _element!!
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) = _next.compareAndSet(expected, update)
}