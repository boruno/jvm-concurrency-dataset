package mpp.fgbank

import kotlinx.atomicfu.locks.*
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(n: Int) {
    private val accounts: Array<Account> = Array(n) { Account() }
//    private val locks : Array<Lock> = Array(n) { Lock() }

    private val lock = ReentrantLock()

    /**
     * Returns current amount in the specified account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @return amount in account.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     */
    fun getAmount(index: Int): Long {
        // TODO: this method has to be made thread-safe.
        lock.lock()
        val ans = accounts[index].amount
        lock.unlock()
        return ans
    }

    /**
     * Returns total amount deposited in this bank.
     */
    fun totalAmount(): Long {
        // TODO: this method has to be made thread-safe.
//        locks.forEach {
//            it.lock()
//        }
        lock.lock()
        val ans: Long = accounts.sumOf { account ->
            account.amount
        }
        lock.unlock()
//        locks.forEach {
//            it.unlock()
//        }
        return ans
    }

    /**
     * Deposits specified amount to account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to deposit.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when deposit will overflow account above [MAX_AMOUNT].
     */
    fun deposit(index: Int, amount: Long): Long {
        // TODO: this method has to be made thread-safe.
        require(amount > 0) { "Invalid amount: $amount" }
    //    locks[index].lock()
        lock.lock()
        val account = accounts[index]
        check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
        account.amount += amount
        val ans = account.amount
       // locks[index].unlock()
        lock.unlock()
        return ans
    }

    /**
     * Withdraws specified amount from account.
     *
     * @param index account index from 0 to [n][numberOfAccounts]-1.
     * @param amount positive amount to withdraw.
     * @return resulting amount in account.
     * @throws IllegalArgumentException when amount <= 0.
     * @throws IndexOutOfBoundsException when index is invalid account index.
     * @throws IllegalStateException when account does not enough to withdraw.
     */
    fun withdraw(index: Int, amount: Long): Long {
        // TODO: this method has to be made thread-safe.
        require(amount > 0) { "Invalid amount: $amount" }
       // locks[index].lock()
        lock.lock()
        val account = accounts[index]
        account.amount -= amount
        val ans = account.amount
       // locks[index].unlock()
        lock.unlock()
        check(ans >= 0) { "Underflow" }
        return ans
    }

    /**
     * Transfers specified amount from one account to another account.
     *
     * @param fromIndex account index to withdraw from.
     * @param toIndex account index to deposit to.
     * @param amount positive amount to transfer.
     * @throws IllegalArgumentException when amount <= 0 or fromIndex == toIndex.
     * @throws IndexOutOfBoundsException when account indices are invalid.
     * @throws IllegalStateException when there is not enough funds in source account or too much in target one.
     */
    fun transfer(fromIndex: Int, toIndex: Int, amount: Long) {
        // TODO: this method has to be made thread-safe.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromIndex != toIndex) { "fromIndex == toIndex" }
//        locks[fromIndex].lock()
//        locks[toIndex].lock()
        lock.lock()
        val amountFrom = accounts[fromIndex].amount
        val amountTo = accounts[toIndex].amount
        accounts[fromIndex].amount -= amount
        accounts[toIndex].amount += amount

//        locks[fromIndex].unlock()
//        locks[toIndex].unlock()
        lock.unlock()
        check(amount <= amountFrom) { "Underflow" }
        check(!(amount > MAX_AMOUNT || amountTo + amount > MAX_AMOUNT)) { "Overflow" }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0
    }
}

/**
 * The maximal amount that can be kept in a bank account.
 */
private const val MAX_AMOUNT = 1000000000000000L
