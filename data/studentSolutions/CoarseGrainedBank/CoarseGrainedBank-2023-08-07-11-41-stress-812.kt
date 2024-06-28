@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

class CoarseGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    // TODO: use this mutex to protect all bank operations.
    private val globalLock = ReentrantLock()

    override fun getAmount(id: Int): Long {
        return accounts[id].amount.get()
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        val currentAmount = account.amount.get()
        check(!(amount > MAX_AMOUNT || currentAmount + amount > MAX_AMOUNT)) { "Overflow" }
        return account.amount.addAndGet(amount)
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        val currentAmount = account.amount.get()
        check(currentAmount - amount >= 0) { "Underflow" }
        return account.amount.addAndGet(-amount)
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromIndex == toIndex" }
        val from = accounts[fromId]
        val to = accounts[toId]
        check(amount <= from.amount.get()) { "Underflow" }
        check(!(amount > MAX_AMOUNT || to.amount.get() + amount > MAX_AMOUNT)) { "Overflow" }
        from.amount.addAndGet(-amount)
        to.amount.addAndGet(amount)
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: AtomicLong = AtomicLong()
    }
}