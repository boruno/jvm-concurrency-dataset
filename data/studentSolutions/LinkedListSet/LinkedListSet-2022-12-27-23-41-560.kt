//package mpp.linkedlistset

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LinkedListSet<E : Comparable<E>> {
    private val first = Node<E>(prev = null, element = Lowest, next = null)
    private val last = Node<E>(prev = null, element = Biggest, next = null)

    init {
        first.setNext(last)
        last.setPrev(first)
    }

    private val head = AtomicReference(first)

    private fun findNodeByElement(element: E): Node<E>? {
        var cur = first
        while (true) {
            when (val curElem = cur.element) {
                Biggest -> {
                    return null
                }
                is Finite<*> -> {
                    val value = (curElem as Finite<E>).value
                    if (value == element) {
                        return if (cur.isDeleted) {
                            null
                        } else {
                            cur
                        }
                    } else if (value < element) {
                        cur = cur.next!!
                    } else {
                        return null
                    }
                }
                Lowest -> {
                    cur = cur.next!!
                }
            }
        }
    }

    private fun lowerBound(element: E): Node<E> {
        var cur = first
        while (true) {
            if (cur.next == null) {
                return cur
            }

            when (val nextElement = cur.next!!.element) {
                Biggest -> {
                    return cur
                }
                is Finite<*> -> {
                    val value = (nextElement as Finite<E>).value
                    if (value < element) {
                        cur = cur.next!!
                    } else {
                        return cur
                    }
                }
                Lowest -> {
                    cur = cur.next!!
                }
            }
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
            val prevNode = lowerBound(element)
            val nextNode = prevNode.next ?: throw Exception("Rofl not happening!")
            if (nextNode.element is Finite<*>) {
                val value = (nextNode.element as Finite<E>).value
                if (value == element) {
                    return false
                }
            }
            if (prevNode.isDeleted || nextNode.isDeleted) {
                continue
            }
            val newNode = Node<E>(prevNode, Finite(element), nextNode)

            if (!nextNode.casPrev(prevNode, newNode)) {
                continue
            }
            if (!prevNode.casNext(nextNode, newNode)) {
                continue
            }
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
            val nodeToRemove = findNodeByElement(element) ?: return false
            if (nodeToRemove.isDeleted)
                return false

            val nextNode = nodeToRemove.next!!
            if (nextNode.prev != nodeToRemove) {
                return false // node is not installed properly
            }
            val prevNode = nodeToRemove.prev!!
            if (prevNode.isDeleted || nextNode.isDeleted) {
                continue
            }

            nodeToRemove.markDeleted()
            nextNode.casPrev(nodeToRemove, prevNode)
            prevNode.casNext(nodeToRemove, nextNode)
            break
        }

        return true
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        return findNodeByElement(element) != null
    }
}

sealed class ComparableWithInfinities
object Lowest : ComparableWithInfinities()
class Finite<E : Comparable<E>>(val value: E) : ComparableWithInfinities()
object Biggest : ComparableWithInfinities()

private class Node<E : Comparable<E>>(prev: Node<E>?, element: ComparableWithInfinities, next: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    private val _prev = AtomicReference(prev)
    private val _next = AtomicReference(next)
    private val deleted = AtomicBoolean(false)

    val element get() = _element

    val isDeleted get() = deleted.get()

    fun markDeleted() {
        deleted.set(true)
    }

    val prev get() = _prev.get()
    fun setPrev(value: Node<E>?) {
        _prev.getAndSet(value)
    }

    fun casPrev(expected: Node<E>?, update: Node<E>?) =
        _prev.compareAndSet(expected, update)

    val next get() = _next.get()
    fun setNext(value: Node<E>?) {
        _next.getAndSet(value)
    }

    fun casNext(expected: Node<E>?, update: Node<E>?) =
        _next.compareAndSet(expected, update)
}