package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.lang.AssertionError

class LinkedListSet<E : Comparable<E>> {
    private val first: ReferenceInterface<E> = Reference(Node<E>(prev = null, element = null, next = null))
    private val last: ReferenceInterface<E> = Reference(Node<E>(prev = first, element = null, next = null))

    init {
        first.getReference1().setNext(last)
    }

    private val head = atomic(first)

    private fun isNextGreater(element: E, next: Node<E>): Boolean {
        // if next node is the last
        if (next.next == null) {
            return true
        }
        return next.prev != null && element < next.element
    }

    private fun isPrevLess(element: E, prev: Node<E>): Boolean {
        // if last node is first
        if (prev.prev == null) {
            return true
        }
        return prev.prev == null || element > prev.element
    }

    private fun findPos(element: E): Pair<ReferenceInterface<E>, ReferenceInterface<E>> {
        find@ while (true) {
            var prev = head.value
            var next = prev.getReference1().next!!

            while (next.getReference1().next != null) {
                if (next is RemovedReference) {
                    prev.getReference1().casNext(next, next.getReference1().next)
                    continue@find
                }

                if (isPrevLess(element, prev.getReference1()) && isNextGreater(element, next.getReference1()) || (next.getReference1().next != null && next.getReference1().element == element)) {
                    return (prev to next)
                }

                prev = next
                next = next.getReference1().next!!
            }

            return (prev to next)
        }
    }

    // нужно игнорировать удаленные
    /**
     * Adds the specified element to this set
     * if it is not already present.
     *
     * Returns `true` if this set did not
     * already contain the specified element.
     */
    fun add(element: E): Boolean {
        // нужно учесть удаленные ссылки
        // нельзя вставить в deleted -> ^ -> alive
        // вставим и deleted сразу потрут, если не нашолся интервал для вставки где alive -> ^ -> alive, то по новой фигачим
        // и по пути затираем next которые removed на next.next таким образом рано или поздно будет alive -> ^ -> alive
        foo@ while (true) {
            val (pred, next) = findPos(element)

            if (next.getReference1().next != null && next.getReference1().element == element) {
                return false
            } else {
                // we can add to deleted node
                val newNode = Reference(Node(pred, element, next))
                if (pred.getReference1().casNext(next, newNode)) {
                    return true
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
        foo@ while (true) {
            val (prev, next) = findPos(element)

            if (next.getReference1().element != element) {
                return false
            } else {
                val nextNext = next.getReference1().next
                val deletedNext = RemovedReference(next.getReference1())
                if (!prev.getReference1().casNext(next, deletedNext)) {
                    continue
                }
                prev.getReference1().casNext(deletedNext, nextNext)
                return true
            }
        }
    }

    // обертку для next, если просто ссылка - то живая нода, если Wrap<ref>, то удаленная
    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        var prev = head.value
        var next = prev.getReference1().next!!

        while (isPrevLess(element, next.getReference1())) {
            prev = next
            next = next.getReference1().next!!
        }

        return (next.getReference1().element == element && next !is RemovedReference)
    }

    private class RemovedReference<E : Comparable<E>>(val reference: Node<E>) : ReferenceInterface<E> {
        override fun getReference1(): Node<E> {
            return reference
        }
    }

    private class Reference<E : Comparable<E>>(val reference: Node<E>) : ReferenceInterface<E> {
        override fun getReference1(): Node<E> {
            return reference
        }
    }
}

private interface ReferenceInterface<E : Comparable<E>> {
    fun getReference1(): Node<E>
}

private class Node<E : Comparable<E>>(prev: ReferenceInterface<E>?, element: E?, next: ReferenceInterface<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element!!

    private val _prev = atomic(prev)
    val prev get() = _prev.value
    fun setPrev(value: ReferenceInterface<E>?) {
        _prev.value = value
    }

    fun casPrev(expected: ReferenceInterface<E>?, update: ReferenceInterface<E>?) =
        _prev.compareAndSet(expected, update)

    private val _next = atomic(next)
    val next get() = _next.value
    fun setNext(value: ReferenceInterface<E>?) {
        _next.value = value
    }

    fun casNext(expected: ReferenceInterface<E>?, update: ReferenceInterface<E>?) =
        _next.compareAndSet(expected, update)
}