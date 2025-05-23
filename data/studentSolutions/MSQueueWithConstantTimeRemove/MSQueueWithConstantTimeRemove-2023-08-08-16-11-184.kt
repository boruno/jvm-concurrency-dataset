@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day2

import java.util.concurrent.atomic.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val newNode = Node(element, curTail)
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                if (curTail.extractedOrRemoved) {
                    curTail.removePhysically()
                }
                return
            }
            tail.compareAndSet(curTail, curTail.next.get())
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val newHead = curHead.next.get() ?: return null
            if (head.compareAndSet(curHead, newHead) && newHead.markExtractedOrRemoved()) {
                newHead.element.let {
                    newHead.element = null
                    return it
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(head.get().prev.get() == null) {
            "`head.prev` must be null"
        }
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.get()
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.get()
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.get()
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

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = AtomicReference<Node<E>?>(null)
        val prev = AtomicReference(prev)

        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            // TODO: As in the previous task, the removal procedure is split into two phases.
            //       First, you need to mark the node as "extracted or removed".
            //       On success, this node is logically removed, and the
            //       operation should return `true` at the end.
            //       In the second phase, the node should be removed
            //       physically, updating the `next` field of the previous
            //       node to `this.next.value`.
            //       In this task, you have to maintain the `prev` pointer,
            //       which references the previous node. Thus, the `remove()`
            //       complexity becomes O(1) under no contention.
            //       Do not remove physical head and tail of the linked list;
            //       it is totally fine to have a bounded number of removed nodes
            //       in the linked list, especially when it significantly simplifies
            //       the algorithm.
            if (!markExtractedOrRemoved()) return false
            removePhysically()
            return true
        }

        fun removePhysically() {
            assert(extractedOrRemoved)
            val prev = this.prev.get()
            val next = this.next.get()
            if (prev != null && next != null) {
                // prevent tail removal, it would be done by enqueue
                prev.next.set(next)
                if (next.extractedOrRemoved) {
                    next.removePhysically()
                }
            }
        }
    }
}