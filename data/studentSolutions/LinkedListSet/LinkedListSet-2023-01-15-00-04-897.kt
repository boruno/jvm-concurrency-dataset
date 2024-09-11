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
        while (true) {
            val prev_node = getElementNode(element)
            val prev_node_nextAndRemoved = prev_node._nextAndRemoved.value
            val new_node = Node(null, element, prev_node_nextAndRemoved.first.value)

            if (prev_node_nextAndRemoved.second.value == false) {
                if (prev_node_nextAndRemoved.first.value == last) {
                    if (prev_node._nextAndRemoved.compareAndSet(prev_node_nextAndRemoved, Node.AtomicMutablePair(new_node, false))) {
                        return true
                    }
                    else {
                        continue
                    }
                }
                else {
                    return false
                }
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
            val prev_node = getElementNode(element)
            val prev_node_nextAndRemoved = prev_node._nextAndRemoved.value
            val prev_node_next = (prev_node_nextAndRemoved.first.value ?: continue)._nextAndRemoved.value.first.value ?: continue

            if (prev_node_nextAndRemoved.second.value != true) {
                if (prev_node_nextAndRemoved.first.value != last) {
                    if (prev_node._nextAndRemoved.compareAndSet(prev_node_nextAndRemoved, Node.AtomicMutablePair(prev_node_next, false))) {
                        return true
                    }
                    else {
                        continue
                    }
                }
                if (prev_node_nextAndRemoved == prev_node._nextAndRemoved.value) {
                    return false
                }
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        while (true) {
            var cur = first
            var cur_nextAndRemoved = cur._nextAndRemoved.value

            while (cur != last && cur_nextAndRemoved.second.value != true) {
                val next = cur_nextAndRemoved.first.value ?: break
                val next_nextAndRemoved = next._nextAndRemoved.value

                if (cur_nextAndRemoved != cur._nextAndRemoved.value) {
                    break
                }

                if (next_nextAndRemoved.second.value == true) {
                    continue
                }

                if (next.element == element) {
                    if (next_nextAndRemoved.second.value != true) {
                        return true
                    }
                }

                cur = next
                cur_nextAndRemoved = next_nextAndRemoved
            }

            if (cur == last) {
                return false
            }
        }
    }

    private fun getElementNode(element: E): Node<E> {
        while (true) {
            var cur = first
            var cur_nextAndRemoved = cur._nextAndRemoved.value

            while (cur_nextAndRemoved.second.value != true) {
                val next = cur_nextAndRemoved.first.value ?: break
                val next_nextAndRemoved = next._nextAndRemoved.value

                if (cur_nextAndRemoved != cur._nextAndRemoved.value) {
                    break
                }

                if (next_nextAndRemoved.second.value == true) {
                    continue
                }

                if (next.element == element) {
                    if (next_nextAndRemoved.second.value != true) {
                        return cur
                    }
                }

                if (next == last) {
                    return cur
                }

                cur = next
                cur_nextAndRemoved = next_nextAndRemoved
            }
        }
    }
}

private class Node<E : Comparable<E>>(prev: Node<E>?, element: E?, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

//    private val _prev = atomic(prev)
//    val prev get() = _prev.value
//    fun setPrev(value: Node<E>?) {
//        _prev.value = value
//    }
//    fun casPrev(expected: Node<E>?, update: Node<E>?) =
//        _prev.compareAndSet(expected, update)
//
//    private val _next = atomic(next)
//    val next get() = _next.value
//    fun setNext(value: Node<E>?) {
//        _next.value = value
//    }
//    fun casNext(expected: Node<E>?, update: Node<E>?) =
//        _next.compareAndSet(expected, update)
    fun setNext(value: Node<E>?) {
        _nextAndRemoved.value.first.value = value
    }

    val _nextAndRemoved = atomic(AtomicMutablePair<E>(next, false))

    class AtomicMutablePair<E : Comparable<E>>(next: Node<E>?, removed: Boolean) {
        val first = atomic(next)
        val second = atomic(removed)
    }
}