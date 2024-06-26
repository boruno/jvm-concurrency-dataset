import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val isLocked : AtomicBoolean = atomic(false)
    val fc_array  = atomicArrayOfNulls<OperationData<E>>(ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        val operation = OperationData<E>(OperationType.POLL, null)
        while(true){
            if(!fc_array[index].compareAndSet(null,operation)){
                index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                continue
            }
            else
            {
                break
            }
        }
        while(true) {
            if (!tryLock()) {
                if(fc_array[index].value!!.operationType.value != OperationType.DONE){
                    continue
                }
                else{
                    val op = fc_array[index].value
                    fc_array[index].compareAndSet(op, null)
                    return op!!.operationValue.value
                }
            }
            else {
                fc_array[index].compareAndSet(operation,null)
                val res = q.poll()
                checkArray()
                unlock()
                return res
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        val operation = OperationData<E>(OperationType.PEEK, null)
        while(true){
            if(!fc_array[index].compareAndSet(null,operation)){
                index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                continue
            }
            else
            {
                break
            }
        }
        while(true) {
            if (!tryLock()) {
                if(fc_array[index].value!!.operationType.value != OperationType.DONE){
                    continue
                }
                else{
                    val op = fc_array[index].value
                    fc_array[index].compareAndSet(op, null)
                    return op!!.operationValue.value
                }
            }
            else {
                fc_array[index].compareAndSet(operation,null)
                val res = q.peek()
                checkArray()
                unlock()
                return res
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        val operation = OperationData<E>(OperationType.ADD, element)
        while(true){
            if(!fc_array[index].compareAndSet(null,operation)){
                index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                continue
            }
            else
            {
                break
            }
        }
        while(true) {
            if (!tryLock() ) {
                if(fc_array[index].value!!.operationType.value != OperationType.DONE){
                    continue
                }
                else{
                    val op = fc_array[index].value
                    fc_array[index].compareAndSet(op, null)
                    return
                }
            }
            else {
                fc_array[index].compareAndSet(operation,null)
                q.add(element)
                checkArray()
                unlock()
                return
            }
        }
    }

    fun tryLock(): Boolean {
        return isLocked.compareAndSet(false,true)
    }

    fun unlock(): Boolean {
        return isLocked.compareAndSet(true,false)
    }


    fun checkArray(){
        for(i in 0 until ARRAY_SIZE){
            val operationData = fc_array[i].value ?: continue
            if(operationData.operationType.value == OperationType.POLL){
                val res = q.poll()
                fc_array[i].value!!.operationType.compareAndSet(OperationType.POLL,OperationType.DONE)
                fc_array[i].value!!.operationValue.compareAndSet(null, res)
                continue
            }
            else if(operationData.operationType.value == OperationType.ADD){
                q.add(operationData.operationValue.value)
                fc_array[i].value!!.operationType.compareAndSet(OperationType.ADD, OperationType.DONE)
                continue
            }
            else if(operationData.operationType.value == OperationType.PEEK){
                val res = q.peek()
                fc_array[i].value!!.operationType.compareAndSet(OperationType.PEEK,OperationType.DONE)
                fc_array[i].value!!.operationValue.compareAndSet(null, res)
                continue
            }
        }
    }
}

enum class OperationType{
    ADD,
    POLL,
    PEEK,
    DONE
}
class OperationData<E>(type: OperationType, value: E?)
{
    val operationType = atomic( type)
    val operationValue = atomic(value)
}

const val ARRAY_SIZE = 4