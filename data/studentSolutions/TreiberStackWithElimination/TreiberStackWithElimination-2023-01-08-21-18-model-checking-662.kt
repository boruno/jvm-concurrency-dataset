package mpp.stackWithElimination

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom
import kotlin.concurrent.thread

class TreiberStackWithElimination<E> {
    private val top = atomic<Node<E>?>(null)
    private val eliminationArray = atomicArrayOfNulls<Pair<E>>(ELIMINATION_ARRAY_SIZE)

    /**
     * Adds the specified element [x] to the stack.
     */
    /**
     * Adds the specified element [x] to the stack.
     */
    fun push(x: E) {
        while (true){
            val oldTop = top.value
            if (top.compareAndSet(oldTop, Node(x,oldTop))){
                return
            }else{
                val rnd = ThreadLocalRandom.current().nextInt(0,2)
                if(eliminationArray[rnd].compareAndSet(null,Pair(x))){
                    for (i in (0..20)){
                        if(eliminationArray[rnd].value!!.done){
                            val pair = eliminationArray[rnd].value
                            eliminationArray[rnd].compareAndSet(pair,null)
                            return
                        }
                    }
                    val pair = eliminationArray[rnd].value
                    if(!eliminationArray[rnd].compareAndSet(pair,null)){
                        val pair = eliminationArray[rnd].value
                        eliminationArray[rnd].compareAndSet(pair,null)
                        return
                    }

                }
            }
        }
    }

    /**
     * Retrieves the first element from the stack
     * and returns it; returns `null` if the stack
     * is empty.
     */
    fun pop(): E? {
        val rnd = ThreadLocalRandom.current().nextInt(0,2)
        val value = eliminationArray[rnd].value
        val done = Pair(value!!.x,true)
        if(value!=null){
            if(!value.done){
                eliminationArray[rnd].compareAndSet(value, done)
            }
        }
        var oldTop = top.value
        while (true){
            oldTop = top.value
            oldTop ?: return null
            if(top.compareAndSet(oldTop,oldTop!!.next)){
                return oldTop.x
            }
        }
    }

    class Pair<E>(x: E ,boolean: Boolean = false) {
        val x = x
        var done = boolean
    }
}

private class Node<E>(val x: E, val next: Node<E>?)

private const val ELIMINATION_ARRAY_SIZE = 2 // DO NOT CHANGE IT