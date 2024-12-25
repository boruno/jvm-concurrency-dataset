//package mpp.stack

class TreiberStack<E> {
    private var top = Node<E?>(null,null)

    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true) {
            val cur = top
            val newTop = Node(x, cur)
            if (cas(cur, newTop)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        while (true) {
            val cur = top
            if (cas(cur, cur.next)) {
                return cur.x
            }
        }
    }

    private fun cas(old: Node<E?>?, new: Node<E?>?): Boolean {
        if (old != top) return false
        top = new!!
        return true
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT