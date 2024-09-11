package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    // private val first: AtomicRef<Node<E>> = atomic(Node(null, Node(false, true, null, null)))
    private val first: AtomicRef<Node<E>> = atomic(Node<E>(true, false, element = null, next = Node<E>(false, true, null, null)))
    // private val last = Node<E>(prev = first, element = null, next = null)
    init {
        // first.setNext(last)
    }

    // private val head = atomic(first)

    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        while (true) {
            val range = getRangeThatContainsValue(element)
            if (range.second.element == element) {
                return false
            }
            if (range.first.next.compareAndSet(range.second, Node(false, false, element, range.second))) {
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
            val range = getRangeThatContainsValue(element)
            if (range.first.element != element) {
                return false
            }
            var next = Node(true, false, null, range.second)
            next.isRemoved.getAndSet(true)
            if (range.first.next.compareAndSet(range.second, next)) {
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val range = getRangeThatContainsValue(element)
        return range.second.element == element
    }

    private fun isNodeLessThanElement(node: Node<E>, element: E): Boolean {
        if (node.isMax) {
            return false
        }
        if (node.isMin) {
            return true
        }
        return node.element < element
    }

    private fun getRangeThatContainsValue(element: E): Pair<Node<E>, Node<E>> {
        while (true) {
            var firstValue = first.value
            var nextValue = firstValue.next.value!!
            while (!firstValue.isRemoved.value && isNodeLessThanElement(nextValue, element) || nextValue.isRemoved.value) {
                if (nextValue.isRemoved.value) {
                    val nextNextValue = nextValue.next.value!!.next.value!!
                    if (firstValue.next.compareAndSet(nextValue, nextNextValue)) {
                        nextValue = nextNextValue
                    } else {
                        firstValue = nextValue
                        nextValue = firstValue.next.value!!
                    }
                }
            }
            if (!firstValue.isRemoved.value) {
                return Pair(firstValue, nextValue)
            } 
        }
    }
}

private class Node<E : Comparable<E>>(_isMin: Boolean, _isMax: Boolean, element: E?, next: Node<E>?) {
    val isRemoved: AtomicBoolean = atomic(false)
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!
    val isMax: Boolean = _isMax
    val isMin: Boolean = _isMin

    // private val _prev = atomic(prev)
    // // val prev get() = _prev.value
    // fun setPrev(value: Node<E>?) {
    //     _prev.value = value
    // }
    // fun casPrev(expected: Node<E>?, update: Node<E>?) =
        // _prev.compareAndSet(expected, update)

    val next = atomic(next)
    // val next get() = _next.value
    // fun setNext(value: Node<E>?) {
    //     _next.value = value
    // }
    // fun casNext(expected: Node<E>?, update: Node<E>?) =
    //     _next.compareAndSet(expected, update)
}
