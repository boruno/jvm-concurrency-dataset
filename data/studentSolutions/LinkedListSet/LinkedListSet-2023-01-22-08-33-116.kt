//package mpp.linkedlistset

import kotlinx.atomicfu.*
import java.util.concurrent.atomic.AtomicMarkableReference

class LinkedListSet<E : Comparable<E>> {
    private val head = Node<E>(
        element = null,
        initialNext = Node(element = null, initialNext = null)
    )

    /**
     * Window.first < element
     * Window.answer >= element
     */
    private fun find(element: E) : Window<E> {
        start@ while (true) {
            var start: Node<E> = head
            var answer: Node<E> = start.nextAndRemoved.reference!!
            val removed = BooleanArray(1)
            var next : Node<E>?

            while (answer.element != null && answer.element!! < element) {
                next = answer.nextAndRemoved.get(removed)!!
                if (removed[0]) {
                    while (removed[0]) {
                        if (!start.nextAndRemoved.compareAndSet(answer, next, false, false)) {
                            continue@start
                        }
                        answer = next!!
                        next = answer.nextAndRemoved.reference!!
                    }
                } else {
                    start = next
                    answer = start.nextAndRemoved.reference!!
                }
            }

            next = answer.nextAndRemoved.get(removed)
            if (next != null && removed[0]) {
                while (removed[0]) {
                    if (!start.nextAndRemoved.compareAndSet(answer, next, false, false)) {
                        continue@start
                    }
                    answer = next!!
                    next = answer.nextAndRemoved.reference!!
                }
            }
            return Window(start, answer)
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
            val window: Window<E> = find(element)
            if (window.answer.element == element) {
                return false
            }
            val new : Node<E> = Node(element, window.answer)
            if (window.start.nextAndRemoved.compareAndSet(window.answer, new, false, false)) {
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
            val window: Window<E> = find(element)
            if (window.answer.nextAndRemoved != element) {
                return false
            }
            val next: Node<E> = window.answer.nextAndRemoved.reference!!
            if (window.answer.nextAndRemoved.compareAndSet(next, next, false, true)) {
                window.start.nextAndRemoved.compareAndSet(window.answer, next, false, false)
                return true
            }
        }
    }

    /**
     * Returns `true` if this set contains
     * the specified element.
     */
    fun contains(element: E): Boolean {
        val window: Window<E> = find(element)
        return window.answer.element == element
    }
}

private class Node<E : Comparable<E>>(element: E?, initialNext: Node<E>?) {
    private val _element = element // `null` for the first and the last nodes
    val element get() = _element

    val nextAndRemoved = AtomicMarkableReference<Node<E>?>(initialNext, false)
}

private class Window<E : Comparable<E>>(var start: Node<E>, var answer: Node<E>)