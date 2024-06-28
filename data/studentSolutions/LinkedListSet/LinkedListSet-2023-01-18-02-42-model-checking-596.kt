package mpp.linkedlistset

import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
    init {
        first.setNext(last)
    }

    private val head = atomic(first)

    private fun findWindow(x: E): Window<E> {
        while (true) {
            var cur: Node<E>? = head.value
            if (cur == null || cur.isRemoved.value) continue
            var next: Node<E>? = cur.next ?: continue
            var flag = false
            while (next?.element!! < x) {
                val node = next.next ?: continue
                if (!node.isRemoved.value) {
                    cur = next
                    next = node
                    next.isRemoved.getAndSet(false)
                } else {
                    val newNext = node.next
                    if (node.isRemoved.value && cur!!._next.compareAndSet(next, newNext)) next = newNext else {
                        flag = true
                        break
                    }
                }
            }
            if (flag) continue
            return Window(cur!!, next)
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
            val window: Window<E> = findWindow(element)
            if (contains(element)) return false
            val newNode: Node<E> = Node(null, element, window.next)
            if (window.cur._next.compareAndSet(window.next, newNode)) return true
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
            val window: Window<E> = findWindow(element)
            if (window.next.element === element) {
                val node: Node<E> = window.next.next ?: return false
                if (node.isRemoved.value) return false
                val newNode = node
                newNode.isRemoved.getAndSet(true)
                if (!window.next._next.compareAndSet(node, newNode)) continue
                window.cur._next.compareAndSet(window.next, node)
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
        val window: Window<E> = findWindow(element)
        val node: Node<E> = window.next.next ?: return false
        return window.next.element === element && !node.isRemoved.value
    }
}

private open class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!
    val isRemoved: AtomicBoolean = atomic(false)

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: Node<E>?) {
        _prev.value = value
    }
    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}

private class Window<E : Comparable<E>> (var cur: Node<E>, var next: Node<E>)

/*package mpp.linkedlistset

import kotlinx.atomicfu.atomic

class LinkedListSet<E : Comparable<E>> {
    private val tail = Node<E>(null, null)
    private val head = Node<E>(null, tail)

    init {
        head.setNext(tail)
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
            val (_, nodeForRemove) = findWindow(element)
            if (nodeForRemove === tail || element != nodeForRemove.element) {
                return false
            }
            if (nodeForRemove.casNext(
                    nodeForRemove, Removed(nodeForRemove.element, nodeForRemove.next)
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
            val nodeAfterWindow = next.next
            if (nodeAfterWindow is Removed) {
                if (!cur.casNext(next, Node(nodeAfterWindow.element, nodeAfterWindow.next))) {
                    return findWindow(element)
                }
                next = nodeAfterWindow
            } else {
                cur = next
                next = cur.next!!
            }
        }
        return Window(cur, next)
    }
}

private class Removed<E : Comparable<E>>(element: E?, next: Node<E>?) : Node<E>(element, next)

private data class Window<E : Comparable<E>>(val cur: Node<E>, val next: Node<E>)

private open class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element
    private val _next = atomic(next)

    val element get() = _element!!
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) = _next.compareAndSet(expected, update)
}*/