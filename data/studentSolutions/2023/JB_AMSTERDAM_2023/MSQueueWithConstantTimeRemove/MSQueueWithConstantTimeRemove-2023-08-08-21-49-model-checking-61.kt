@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import java.util.concurrent.atomic.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val headRef: AtomicReference<Node<E>>
    private val tailRef: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        headRef = AtomicReference(dummy)
        tailRef = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tailRef.get()
            val newNode = Node(element, curTail)
            if (curTail.nextRef.compareAndSet(null, newNode)) {
                // safe section, all other enqueue threads wait on the loop until tailRef is updated

                newNode.prevRef.set(curTail)
                tailRef.set(newNode)
            }
        }

        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
//        TODO("Implement me!")
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = headRef.get()
            val node = curHead.nextRef.get() ?: return null
            if (node.prevRef.compareAndSet(curHead, null)) {
                // safe section, all other dequeue threads wait on the loop until headRef is updated

                headRef.set(node)
                val element = node.element
                node.element = null
                return element
            }
        }

        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
//        TODO("Implement me!")
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = headRef.get()
        while (true) {
            val next = node.nextRef.get()
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
        check(headRef.get().prevRef.get() == null) {
            "`head.prev` must be null"
        }
        check(tailRef.get().nextRef.get() == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = headRef.get()
        while (true) {
            if (node !== headRef.get() && node !== tailRef.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.nextRef.get()
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prevRef.get()
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
        val nextRef = AtomicReference<Node<E>?>(null)
        val prevRef = AtomicReference(prev)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
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
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: In this task, you have to maintain the `prev` pointer,
            // TODO: which references the previous node. Thus, the `remove()`
            // TODO: complexity becomes O(1) under no contention.
            // TODO: Do not remove physical head and tail of the linked list;
            // TODO: it is totally fine to have a bounded number of removed nodes
            // TODO: in the linked list, especially when it significantly simplifies
            // TODO: the algorithm.
            TODO("Implement me!")
        }
    }
}