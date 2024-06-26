package mpp.linkedlistset

import kotlinx.atomicfu.atomic

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(true, element = null, next = null)
    private val last = Node<E>(true, element = null, next = null)

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
            val window = findWindow(element)
            if (contains(element)) return false
            val node = Node(true, element, window.next)
            if (window.cur._next.compareAndSet(window.next, node)) return true
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
        /*while (true) {
            val (_, nodeForRemove) = findWindow(element)
            if (nodeForRemove == tail.value || element != nodeForRemove.element) {
                return false
            }
            val removed = Node(false, nodeForRemove.element, nodeForRemove.next)
            if (nodeForRemove.casNext(
                    nodeForRemove, removed
                )
            ) {
                return true
            }
        }*/
        while (true) {
            val (cur, next) = findWindow(element)
            if (next.element == element) {
                val node = next.next ?: continue
                if (!node.isActive.value) return false
                val newNode = Node(false, node.element, node.next)
                if (!next._next.compareAndSet(node, newNode)) continue
                cur._next.compareAndSet(next, node)
                return true
            }
            return false
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
        while (true) {
            var cur = head.value
            var next = cur.next!!
            var flag = false
            while (true) {
                val node = next.next
                when {
                    next == tail.value -> {
                        flag = false
                        break
                    }

                    next.element >= element || node == null -> break
                    node.isActive.value -> {
                        cur = next
                        next = cur.next!!
                    }

                    cur._next.compareAndSet(next, Node(true, node.element, node.next)) -> next = node
                    else -> {
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

private class Node<E : Comparable<E>>(mode: Boolean, element: E?, next: Node<E>?) {
    private val _element = element
    val _next = atomic(next)
    val isActive = atomic(mode)

    val element get() = _element!!
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) = _next.compareAndSet(expected, update)
}