@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day3

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null).apply { markExtractedOrRemoved() }
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val newTail = Node(element, null)
        while (true) {
            val curTail = tail.value
            newTail.prev.value = curTail
            if (curTail.next.compareAndSet(null, newTail)) {
                // MAY STALE, see ASSISTANCE
                tail.compareAndSet(curTail, newTail)
                if (curTail.extractedOrRemoved) {
                    curTail.doRemove()
                }
                // SUCCESS
                return
            } else {
                // ASSISTANCE
                // Since CAS#1 failed and next can only go null→not null,
                // it's not null
                val assistTail = curTail.next.value ?: return
                tail.compareAndSet(curTail, assistTail)
                // end of assistance, RETRY
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, curHeadNext)) {
                curHeadNext.prev.value = null
                if (curHeadNext.markExtractedOrRemoved()) {
                    return curHeadNext.element
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.value
        while (true) {
            val next = node.next.value
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun checkNoRemovedElements() {
        check(head.value.extractedOrRemoved) {
            "`head` must be removed, isn't, state: $this"
        }
        check(head.value.prev.value == null) {
            "`head.prev` must be null, state: $this"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue, state: $this"
                }
            }
            val nodeNext = node.next.value
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.value
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue, state: $this"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, state: $this"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    /**
     * This is a string representation of the data structure,
     * you see it in Lincheck tests when they fail.
     * DO NOT CHANGE THIS CODE.
     */
    override fun toString(): String {
        // Choose the leftmost node.
        var node = head.value
        var prev: Node<E>? = null
        if (tail.value.next.value === node) {
            node = tail.value
        }
        // Traverse the linked list.
        return buildString {
            while (true) {
                if (head.value === node) append("H = ")
                if (tail.value === node) append("T = ")
                append("<${node.element}")
                if (node.extractedOrRemoved) append(" !")
                val prevVal = node.prev.value
                if (prevVal !== prev) {
                    append(" p:")
                    if (prevVal == null)
                        append("nullNode")
                    else
                        append(prevVal.element)
                }
                append(">")
                prev = node
                node = node.next.value ?: break
                append(", ")
            }
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        fun doRemove() {
            val next = next.value ?: return
            val prev = prev.value ?: return
            //this.prev.value = null
            //this.next.value = null

            prev.next.compareAndSet(this, next)
            next.prev.value = prev

            if (prev.extractedOrRemoved) prev.doRemove()
            if (next.extractedOrRemoved) next.doRemove()
        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            if (!markExtractedOrRemoved()) return false

            doRemove()
            return true
        }
    }
}