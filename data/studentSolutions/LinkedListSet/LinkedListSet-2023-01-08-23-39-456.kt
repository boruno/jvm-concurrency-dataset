//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first: WrappedNode<E> = SomeNode(Node<E>(prev = null, element = null, next = null))
    private val last: WrappedNode<E> = SomeNode(Node<E>(prev = first, element = null, next = null))

    init {
        first.node.setNext(last)
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
        outer@ while (true) {

            var prev: WrappedNode<E> = head.value
            var current = prev.node.next
            while (current != last && current != null) {
                if (current.node.element == element && current is Node<E>) {
                    return !current.removed.value
                }
                if (prev.node.element <= element && (current == last || element < current.node.element)) {
                    val answer = SomeNode(Node(null, element, current))
                    if (prev.node.casNext(current, answer)) {
                        return true
                    }
                    continue@outer
                }

                if (prev.node.element > element) {
                    return false
                }
                prev = current
                current = current.node.next
            }
            if (current == last) {
                val answer = SomeNode(Node(null, element, current))
                if (prev.node.casNext(current, answer)) {
                    return true
                }
                continue
            }
            return false
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
    outer@ while (true) {
        var prev = head.value
        var current = prev.node.next
        while (current != null && current != last && current.node.element <= element) {
            if (current is SomeNode<E> && !current.isRemoved() && current.node.element == element) {
                val removed = RemovedNode(current.node)
                if (current.setted.compareAndSet(current.inner, removed)) {
                    if (prev is SomeNode<E> && prev.setted.compareAndSet(prev.inner, current.node.next!!)) {
                        return true
                    }
                }
                continue@outer
            }
            prev = current
            current = current.node.next
        }
        return false
    }
}

/**
 * Returns `true` if this set contains
 * the specified element.
 */
fun contains(element: E): Boolean {
    var current = head.value.node.next
    while (current != null && current != last && current.node.element <= element) {
        if (current.node.element == element && current is SomeNode<E> && !current.isRemoved()) {
            return true
        }
        current = current.node.next
    }
    return false
}
}

private interface WrappedNode<E : Comparable<E>> {
    val node: Node<E>
}

private class Node<E : Comparable<E>>(prev: WrappedNode<E>?, element: E?, next: WrappedNode<E>?) : WrappedNode<E> {
    val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    override val node = this

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: WrappedNode<E>?) {
        _prev.value = value
    }

    fun casPrev(expected: WrappedNode<E>?, update: WrappedNode<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: WrappedNode<E>?) {
        _next.value = value
    }

    fun casNext(expected: WrappedNode<E>?, update: WrappedNode<E>?) =
        _next.compareAndSet(expected, update)

    val removed = atomic(false)
}

private class RemovedNode<E : Comparable<E>>(override val node: Node<E>) : WrappedNode<E>

private class SomeNode<E : Comparable<E>>(val inner: Node<E>) : WrappedNode<E> {
    val setted = atomic<WrappedNode<E>>(inner)
    override val node: Node<E>
        get() = setted.value.node

    fun isRemoved() = setted.value is Node<E>

}


fun main() {
    val q = LinkedListSet<Int>()
    q.add(2)
    println(q.contains(2))
}

