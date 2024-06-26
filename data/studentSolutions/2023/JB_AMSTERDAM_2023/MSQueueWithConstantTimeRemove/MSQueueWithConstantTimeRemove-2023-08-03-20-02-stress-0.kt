@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val oldTail = tail.value
            val newTail = Node(element, prev = oldTail)
            //  h       t
            // (d) <-> (OT)
            if (tail.compareAndSet(oldTail, newTail)) {
                // Tail is advanced
                //  h              t
                // (d) <-> (OT) <- (NT)

                if(oldTail.next.compareAndSet(null, newTail)) {
                    // Link previous node with new tail
                    //  h                t
                    // (d) <-> (OT) <-> (NT)

                    // Corner case: previous tail is logically removed -> remove it now
                    if (oldTail.extractedOrRemoved) oldTail.removeForReal()
                    //  h       t
                    // (d) <-> (NT)
                }

                return
            }
        }
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
        // TODO("Implement me!")
    }

    override fun dequeue(): E? {
        //region State before execution:
        //  h               t
        // (d) <-> (1) <-> (2)
        //
        // 1 should be returned
        // node (1) should become dummy and also marked as removed -> (1')
        // (d) should be physically removed i.e. no one points to it
        //          h       t
        // null <- (1') <-> (2)
        //endregion

        while (true) {
            val currentHead = head.value

            val extractingNode = currentHead.next.value
            // Corner case: Node after head is null
            //          h
            // null <- (d) -> null
            if (extractingNode == null) return null

            if (head.compareAndSet(currentHead, extractingNode)) {
                //region Head is advanced
                //          h       t
                // (d) <-> (1) <-> (2)
                //endregion

                if (extractingNode.markExtractedOrRemoved()) {
                    //region Current thread made deletion?
                    //          h        t
                    // (d) <-> (1') <-> (2)
                    //endregion
                    return extractingNode.element
                } else {
                    //region If no, then someone did mark it as "removed" then try to dequeue again
                    // Lemma: Can some other thread mark it as removed via dequeue method?
                    // Let's assume YES! Then this other thread also managed to advance head to extractingNode
                    // i.e. it succeeds in this CAS: head.CAS(currentHead, extractingNode) => true!
                    // It is possible only if "next" of at least two nodes (N1, N2) points to extractingNode (EN)
                    // i.e. N1.next == EN && N2.next == EN
                    // This isn't possible in linked list without cycles
                    // Therefore: this node was marked as "removed" by "remove" method.
                    //endregion
                }
            }
        }

        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
        // TODO("Implement me!")
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
    override fun validate() {
        check(head.value.prev.value == null) {
            "`head.prev` must be null"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.value
        while (true) {
//            if (node !== head.value && node !== tail.value) {
//                check(!node.extractedOrRemoved) {
//                    "Removed node with element ${node.element} found in the middle of the queue"
//                }
//            }
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

        fun removeForReal() {

        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            markExtractedOrRemoved()
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