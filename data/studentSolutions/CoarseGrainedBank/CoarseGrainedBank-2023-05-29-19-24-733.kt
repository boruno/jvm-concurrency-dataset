@file:Suppress("DuplicatedCode")

//package day1

import Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class CoarseGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    // TODO: use this mutex to protect all bank operations.
    private val globalLock = ReentrantLock()

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        return accounts[id].amount
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        try {
            globalLock.lock()

            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        } finally {
            globalLock.unlock()
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        try {
            globalLock.lock()

            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } finally {
            globalLock.unlock()
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()

        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromIndex == toIndex" }
        val from = accounts[fromId]
        val to = accounts[toId]
        check(amount <= from.amount) { "Underflow" }
        check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
        from.amount -= amount
        to.amount += amount

        globalLock.unlock()
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