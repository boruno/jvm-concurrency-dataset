@file:Suppress("DuplicatedCode")

//package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class CoarseGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    // TODO: use this mutex to protect all bank operations.
    private val globalLock = ReentrantLock()

    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()
        val result = accounts[id].amount
        globalLock.unlock()
        return result
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        var accAmount = account.amount
        globalLock.lock()
        try {
            check(!(amount > MAX_AMOUNT || accAmount + amount > MAX_AMOUNT)) { "Overflow" }
            accAmount += amount
        } finally {
            globalLock.unlock()
        }
        return accAmount
    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        var accAmount = account.amount
        globalLock.lock()
        try {
            check(accAmount - amount >= 0) { "Underflow" }
            accAmount -= amount
        } finally {
            globalLock.unlock()
        }
        return accAmount
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromIndex == toIndex" }
        val from = accounts[fromId]
        val to = accounts[toId]
        globalLock.lock()
        try {
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
        } finally {
            globalLock.unlock()
        }
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