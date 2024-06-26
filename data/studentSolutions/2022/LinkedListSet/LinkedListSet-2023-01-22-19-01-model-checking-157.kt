package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = null, next = null)
    private val last = Node<E>(prev = first, element = null, next = null)
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
        while(true) {
            findPlace(element) { node, next ->
                if (node.element == element) return false
                val newNode = Node(prev = node, element = element, next = next)
                if(node.casNext(next, newNode)) return true
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
        while(true) {
            findPlace(element) { node, next ->
                if (node.element != element) return false
                node.removed = true
                node.prev?.setNext(next)
                if(node.prev?.removed == false){
                    next.setPrev(node.prev)
                    if(!next.removed) return true
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        findPlace(element) { node, _ ->
            return node.element == element
        }
        return false
    }

    private inline fun findPlace(element: E, action: (node: Node<E>, next: Node<E>) -> Unit) {
        var current: Node<E>? = first
        while (current != null){
            val next = current.next ?: break
            if(current.element == element){
                action(current, next)
                break
            }
            if(next == last || next.element < element){
                action(current, next)
                break
            }
            current = current.next
        }
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
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

    var removed: Boolean = false
}