import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

private const val ARRAY_SIZE = 3
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val locked = atomic<Boolean>(false)
    private val fc_array = atomicArrayOfNulls<Operation<E?>?>(ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (locked.compareAndSet(false, true)){
            val ans = q.poll()
            combiner()
            locked.value = false
            return ans
        }
        val newOperation = Operation<E?>("poll", null)
        var randomInteger: Int
        while (true){
            randomInteger = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            if (fc_array[randomInteger].compareAndSet(null, newOperation)){
                break
            }
        }
        while (true){
            if (locked.compareAndSet(false, true)){
                val ans = q.poll()
                combiner()
                locked.value = false
                return ans
            }
            val tempArrElement = fc_array[randomInteger].value
            if (tempArrElement!!.name == "answer" && fc_array[randomInteger].compareAndSet(tempArrElement, null)){
                return tempArrElement.x
            }
        }
    }
    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (locked.compareAndSet(false, true)){
            val ans = q.peek()
            combiner()
            locked.value = false
            return ans
        }
        val newOperation = Operation<E?>("peek", null)
        var randomInteger: Int
        while (true){
            randomInteger = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            if (fc_array[randomInteger].compareAndSet(null, newOperation)){
                break
            }
        }
        while (true){
            if (locked.compareAndSet(false, true)){
                val ans = q.peek()
                combiner()
                locked.value = false
                return ans
            }
            val tempArrElement = fc_array[randomInteger].value
            if (tempArrElement!!.name == "answer" && fc_array[randomInteger].compareAndSet(tempArrElement, null)){
                return tempArrElement.x
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (locked.compareAndSet(false, true)){
            q.add(element)
            combiner()
            locked.value = false
            return
        }
        val newOperation = Operation<E?>("add", element)
        var randomInteger: Int
        while (true){
            randomInteger = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            if (fc_array[randomInteger].compareAndSet(null, newOperation)){
                break
            }
        }
        while (true){
            if (locked.compareAndSet(false, true)){
                q.add(element)
                combiner()
                locked.value = false
                return
            }
            val tempArrElement = fc_array[randomInteger].value
            if (tempArrElement!!.name == "answer" && fc_array[randomInteger].compareAndSet(tempArrElement, null)){
                return
            }
        }
    }

    private fun combiner(){
        for (i in 0 until fc_array.size){
            val operation = fc_array[i].value
            if (operation != null && operation.name == "peek"){
                fc_array[i].compareAndSet(operation, Operation("answer", q.peek()))
            }
            if (operation != null && operation.name == "poll"){
                fc_array[i].compareAndSet(operation, Operation("answer", q.poll()))
            }
            if (operation != null && operation.name == "add"){
                q.add(operation.x)
                fc_array[i].compareAndSet(operation, Operation("answer", null))
            }
        }
    }
}

private class Operation<E>(val name: String, val x: E)