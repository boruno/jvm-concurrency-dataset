package mpp.linkedlistset

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

    private fun findNotDeletedNodeByElement(element: E): Node<E>? {
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
                        cur = getNextNotDeleted(cur)
                    } else {
                        return null
                    }
                }

                Lowest -> {
                    cur = getNextNotDeleted(cur)
                }
            }
        }
    }

    private fun findNonDeletedNodeWithLowerElement(element: E): Node<E> {
        var cur = first
        while (true) {
            if (cur.next == null) {
                return cur // should not be called
            }

            val nextExisting = getNextNotDeleted(cur)

            when (val nextElement = nextExisting.element) {
                Biggest -> {
                    return cur
                }

                is Finite<*> -> {
                    val value = (nextElement as Finite<E>).value
                    if (value < element) {
                        cur = nextExisting
                    } else {
                        return cur
                    }
                }

                Lowest -> {
                    cur = nextExisting
                }
            }
        }
    }

    private fun getNextNotDeleted(node: Node<E>): Node<E> {
        var result = node.next!!
        while (result.isDeleted) {
            result = result.next!!
        }
        return result
    }

    private fun getPreviousNotDeleted(node: Node<E>): Node<E> {
        var result = node.prev!!
        while (result.isDeleted) {
            result = result.prev!!
        }
        return result
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
            val prevNode = findNonDeletedNodeWithLowerElement(element)
            val nextNode = getNextNotDeleted(prevNode)
            if (nextNode.element is Finite<*>) {
                val value = (nextNode.element as Finite<E>).value
                if (value == element && !nextNode.isDeleted) {
                    return false
                }
            }
            val newNode = Node(prevNode, Finite(element), nextNode)

            if (!prevNode.casNext(nextNode, newNode)) {
                continue
            }
            nextNode.casPrev(prevNode, newNode)
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
            val nodeToRemove = findNotDeletedNodeByElement(element) ?: return false

            val nextNode = nodeToRemove.next!!
            if (nextNode.prev != nodeToRemove) {
                return false // node is not installed properly
            }
            val prevNode = nodeToRemove.prev!!
            

            nodeToRemove.markDeletedWithCas()
            prevNode.casNext(nodeToRemove, nextNode)
            nextNode.casPrev(nodeToRemove, prevNode)
            break
        }

        return true
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        return findNotDeletedNodeByElement(element) != null
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

    fun markDeletedWithCas(): Boolean {
        return deleted.compareAndSet(false, true)
    }

    val prev get() = _prev.get()
    fun setPrev(value: Node<E>?) {
        _prev.getAndSet(value)
    }

    fun casPrev(expected: Node<E>?, update: Node<E>?): Boolean {
        if (!deleted.get())
            return _prev.compareAndSet(expected, update)
        return false

    }

    val next get() = _next.get()
    fun setNext(value: Node<E>?) {
        _next.getAndSet(value)
    }

    fun casNext(expected: Node<E>?, update: Node<E>?): Boolean {
        if (!deleted.get())
            return _next.compareAndSet(expected, update)
        return false
    }
}