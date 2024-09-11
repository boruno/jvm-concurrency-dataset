import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean


class FCPriorityQueue<E : Comparable<E>> {
    var locked = AtomicBoolean(false)
    var DONE = "Done"
    val SIZE = 50
    val q = PriorityQueue<E>()
    private val array = atomicArrayOfNulls<Pair<String, E?>>(SIZE)

    private fun tryLocked(): Boolean{
        return locked.compareAndSet(false, true)
    }
    private fun unLock(){
        locked.compareAndSet(true, false)
    }

    private fun helpOtherFlow() {
        for (i in 0 until SIZE) {
            if (array[i].value != null && array[i].value!!.first != DONE) {
                if (array[i].value!!.first == "poll") {
                    array[i].value = Pair(DONE, q.poll())
                }
                else if (array[i].value!!.first == "peek") {
                    array[i].value = Pair(DONE, q.peek())
                }
                else if (array[i].value!!.first == "add") {
                    q.add(array[i].value!!.second)
                    array[i].value = Pair(DONE, null)
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true){
            if(tryLocked()){
                val ans = q.poll()
                helpOtherFlow()
                unLock()
                return ans
            }
            else {
                var staticIndex: Int? = null
                while(true) {
                    val index = ThreadLocalRandom.current().nextInt(SIZE)
                    if (array[index].compareAndSet(null, Pair("poll", null))) {
                        staticIndex = index
                        break
                    }
                }
                val res = array[staticIndex!!].value
                array[staticIndex].compareAndSet(res, null)
                if(array[staticIndex].value!!.first == DONE){
                    return res!!.second
                }

            }
        }
    }


    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true){
            if(tryLocked()){
                val ans = q.peek()
                helpOtherFlow()
                unLock()
                return ans
            }
            else {
                var staticIndex: Int? = null
                while(true) {
                    val index = ThreadLocalRandom.current().nextInt(SIZE)
                    if (array[index].compareAndSet(null, Pair("peek", null))) {
                        staticIndex = index
                        break
                    }
                }
                val res = array[staticIndex!!].value
                array[staticIndex].compareAndSet(res, null)
                if(array[staticIndex].value!!.first == DONE){
                    return res!!.second
                }

            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true){
            if(tryLocked()){
                q.add(element)
                helpOtherFlow()
                unLock()
                return
            }
            else {
                var staticIndex: Int? = null
                while(true) {
                    val index = ThreadLocalRandom.current().nextInt(SIZE)
                    if (array[index].compareAndSet(null, Pair("add", element))) {
                        staticIndex = index
                        break
                    }
                }
                val res = array[staticIndex!!].value
                array[staticIndex].compareAndSet(res, null)
                if(array[staticIndex].value!!.first == DONE){
                    return
                }

            }
        }
    }
}