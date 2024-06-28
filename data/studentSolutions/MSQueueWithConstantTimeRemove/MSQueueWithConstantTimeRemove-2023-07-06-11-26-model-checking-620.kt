@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = object: Node<E>(element = null, prev = null) {
            override val id: String = "DUMMY"
        }
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            val newNode = Node(element, currentTail)
            val next = currentTail.next
            if (next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)

                // When adding a new node, check whether
                // the previous tail is logically removed.
                // If so, remove it physically from the linked list.
                if (currentTail.extractedOrRemoved) {
                    currentTail.removePhysically()
                }
                return
            }
            else {
                tail.compareAndSet(currentTail, next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        // After moving the `head` pointer forward,
        // mark the node that contains the extracting
        // element as "extracted or removed", restarting
        // the operation if this node has already been removed.
        while (true) {
            val currentHead = head.value
            val next = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, next)) {
                head.value.prev.value = null
                if (next.markExtractedOrRemoved()) {
                    next.removePhysically()

                    // nullify the dequeued element to avoid a memory leak
                    val element = next.element
                    next.element = null
                    return element
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

    private open class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)

        open val id :String = "@" + Integer.toHexString(hashCode())

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

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

            // The removal procedure is split into two phases.
            // First, you need to mark the node as "extracted or removed".
            // On success, this node is logically removed, and the
            // operation should return `true` at the end.
            if (markExtractedOrRemoved()) {
                // In the second phase, the node should be removed
                // physically, updating the `next` field of the previous
                // node to `this.next.value`.
                removePhysically()
                return true
            }
            return false
        }

        fun removePhysically() {
            val curPrev = this.prev.value
            val curNext = next.value

            // Do not remove `head` and `tail` physically to make
            // the algorithm simpler. In case a tail node is logically removed,
            // it will be removed physically by `enqueue(..)`.
            if (curNext == null || curPrev == null) {
                return
            }

            curPrev.next.compareAndSet(this, curNext)
            curNext.prev.compareAndSet(this, curPrev)

            // Help to remove nodes which were already marked for removal
            if (curPrev.extractedOrRemoved) {
                curPrev.removePhysically()
            }
            if (curNext.extractedOrRemoved) {
                curNext.removePhysically()
            }
        }
    }
}