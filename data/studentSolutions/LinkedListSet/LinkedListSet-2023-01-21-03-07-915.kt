//package mpp.linkedlistset

import kotlinx.atomicfu.*

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(element = null, next = null)
    private val last = Node<E>(element = null, next = null)

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
            val node = head
            var nodeNext = node.value.next

            while (nodeNext?.element != null && nodeNext.element!! < element) {
                if (!nodeNext.alive) {
                    break
                }
                node.value = nodeNext
                nodeNext = node.value.next
            }

            if (nodeNext?.optElement() != null && !nodeNext.alive) {
                continue
            }

            if (nodeNext?.optElement() != null && nodeNext.element == element) {
                return false
            }

            val newNode = Node<E>(element, nodeNext)
            if (node.value.casNext(nodeNext, newNode)) {
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
            val node = head
            var nodeNext = node.value.next

            while (nodeNext?.optElement() != null && nodeNext.element < element) {
                if (!nodeNext.alive) {
                    break
                }
                node.value = nodeNext
                nodeNext = node.value.next
            }

            if (nodeNext?.optElement() != null && !nodeNext.alive) {
                continue
            }

            if (nodeNext?.optElement() != null && nodeNext.element == element) {
                nodeNext.kill()
                if (node.value.casNext(nodeNext, nodeNext.next)) {
                    return true
                }
            } else if (nodeNext == null || nodeNext.element > element) {
                return false
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        while (true) {

            val node = head
            var nodeNext = node.value.next

            while (nodeNext?.optElement() != null && nodeNext.element < element) {
                if (!nodeNext.alive) {
                    break
                }
                node.value = nodeNext
                nodeNext = node.value.next
            }

            if (nodeNext?.optElement() != null && !nodeNext.alive) {
                continue
            }

            if (nodeNext?.optElement() != null && nodeNext.element == element) {
                return true
            }
            return false
        }
    }

    private class Node<E : Comparable<E>>(element: E?, next: Node<E>?) {
        private val _element = element // `null` for the first and the last nodes
        private val _nextAndAlive = atomic(Pair(next, true))
        val element get() = _element!!
        val next get() = _nextAndAlive.value.first

        val alive get() = _nextAndAlive.value.second

        fun optElement() = _element
        fun setNext(value: Node<E>?) {
            _nextAndAlive.value = Pair(value, _nextAndAlive.value.second)
        }

        fun kill() {
            _nextAndAlive.value = Pair(next, false)
        }

        fun casNext(expected: Node<E>?, update: Node<E>?) =
            _nextAndAlive.compareAndSet(Pair(expected, true), Pair(update, true))
    }
}