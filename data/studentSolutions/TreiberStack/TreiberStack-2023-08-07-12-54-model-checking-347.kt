package day1

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.random.Random

class TreiberStack<E> : Stack<E> {
    // Initially, the stack is empty.
    private val top = AtomicReference<Node<E>?>(null)

    private val elim = AtomicReferenceArray<Cell<E>>(5)

    override fun push(element: E) {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.

        val idx = rand.nextInt(0, elim.length() - 1)
        val cell = Cell(element, false)
        if (elim.compareAndSet(idx, null, cell)) {
            Thread.sleep(2)
            val updatedCell = elim.get(idx)
            if (updatedCell.done) {
                if (elim.compareAndSet(idx, updatedCell, null)) {
                    return
                }
            }
        }

        while (true) {
            val curTop = top.get()
            val newTop = Node(element, curTop)
            if (top.compareAndSet(curTop, newTop)) {
                break
            }
        }
    }

    override fun pop(): E? {
        // TODO: Make me linearizable!
        // TODO: Update `top` via Compare-and-Set,
        // TODO: restarting the operation on CAS failure.
        val idx = rand.nextInt(0, elim.length() - 1)
        val cell = elim.get(idx)
        if (cell != null && !cell.done) {
            if (elim.compareAndSet(idx, cell, Cell(cell.element, true))) {
                return cell.element
            }
        }

        while (true) {
            val curTop = top.get() ?: return null
            val newTop = curTop.next
            if (top.compareAndSet(curTop, newTop)) {
                return curTop.element
            }
        }
    }

    private class Cell<E>(
        val element: E,
        val done: Boolean,
    )

    private class Node<E>(
        val element: E,
        val next: Node<E>?
    )

    companion object {
        val rand = Random(System.currentTimeMillis())
    }
}