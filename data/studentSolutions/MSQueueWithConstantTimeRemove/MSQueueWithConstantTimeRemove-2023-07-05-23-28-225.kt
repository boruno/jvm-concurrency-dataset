@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day2

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(element = null, prev = null)
        dummy.markExtractedOrRemoved()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val current = tail.value
            val next = current.next.value
            if (next != null) {
                tail.compareAndSet(current, next)
                continue
            }
            val newNode = Node(element, current)
            if (current.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(current, newNode)
                if (current.extractedOrRemoved) current.physicallyRemove()
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val current = head.value
            val next = current.next.value ?: return null
            val success = next.markExtractedOrRemoved()
            if (head.compareAndSet(current, next)) {
                next.prev.value = null
            }
            if (success) return next.element
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
        check(head.value.prev.value == null) {
            "`head.prev` must be null"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.value
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.value
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private inner class Node(
        var element: E?,
        prev: Node?
    ) {
        val next = atomic<Node?>(null)
        val prev = atomic(prev)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean = markExtractedOrRemoved().also { if (it) physicallyRemove() }

        fun physicallyRemove() {
            while (true) {
                val previous = prev.value ?: return
                val next = next.value ?: return
                val curHead = head.value
                if (previous === curHead && curHead.next.value === this) {
                    if (head.compareAndSet(previous, this)) {
                        prev.value = null
                    }
                    return
                }

//                if (previous !== prev.value) continue
                if (!previous.extractedOrRemoved) {
                    previous.next.compareAndSet(this, next)
                }
                if (this != head.value)
                    next.prev.compareAndSet(this, previous)
                if (previous.extractedOrRemoved) previous.physicallyRemove()
                if (next.extractedOrRemoved) next.physicallyRemove()
                return
            }
        }
    }

    fun moveHead() {
        while (true) {
            val node = head.value
            val next = node.next.value ?: break
            if (!next.extractedOrRemoved) break
            if (head.compareAndSet(node, next)) {
                next.prev.value = null
            }
        }
    }
}