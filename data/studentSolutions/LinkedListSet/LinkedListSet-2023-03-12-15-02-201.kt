package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(null, null)
    private val last = Node<E>(null, null)
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
        while (true) {
            var cur = head.value
            var next = cur.next

            while (next != last) {
                if (next?.element == element) return false
                if (cur.isNextRemoved()) {
                    cur = next!!
                    next = cur.next
                    continue
                }

                val isCurrentLess =  cur.element < element
                val isNextGreater = next!!.element > element
                if (isCurrentLess && isNextGreater) {
                    val newNode = Node(element, next)
                    if (cur.casNext(next, newNode)) return true
                    break
                }

                cur = next
                next = cur.next
            }

            if (next != last) continue
            if (!cur.casNext(last, Node(element, last))) continue

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
        while (true) {
            var cur = head.value
            var next = cur.next

            while (next != last) {
                if (cur.isNextRemoved() || next!!.element != element) {
                    cur = next!!
                    next = cur.next
                    continue
                }

                if (!tryRemove(cur, next))
                    break

                return true
            }

            if (next == last) return false
        }
    }

    private fun tryRemove(cur: Node<E>, next: Node<E>) : Boolean {
        val nextNext = next.next

        val isNextRemoved = next.isNextRemoved()
        val isNextNextCasSuccessful = next.casNext(nextNext, Removed(nextNext?.element, nextNext?.next))
        val isNextCasSuccessful = cur.casNext(next, nextNext)

        return !isNextRemoved && isNextCasSuccessful && isNextNextCasSuccessful
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var current = head.value
        var next = current.next

        while (next != last) {
            current = next!!
            next = current.next
            if (current.element == element && !current.isNextRemoved()) {
                return true
            }
        }
        return false
    }
}

private open class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: Node<E>?) {
        _next.value = value
    }
    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)

    fun isNextRemoved(): Boolean {
        return _next.value is Removed
    }
}

private class Removed<E : Comparable<E>> : Node<E> {
    constructor(element: E?, next: Node<E>?) : super(element, next)
}
