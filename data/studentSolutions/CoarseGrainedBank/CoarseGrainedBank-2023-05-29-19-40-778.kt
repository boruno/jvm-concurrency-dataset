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
        globalLock.lock()
        val result = accounts[id].amount
        globalLock.unlock()
        return result
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        try {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
        } finally {
            account.amount += amount
        }
        globalLock.unlock()
        return account.amount

    }

    override fun withdraw(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via coarse-grained locking.
        globalLock.lock()
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        try {
            check(account.amount - amount >= 0) { "Underflow" }
        } finally {
            account.amount -= amount
        }
        globalLock.unlock()
        return account.amount
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via coarse-grained locking.

        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromIndex == toIndex" }
        val from = accounts[fromId]
        globalLock.lock()
        try{
            check(amount <= from.amount) { "Underflow" }
        } finally {
            from.amount -= amount
        }
        globalLock.unlock()

        val to = accounts[toId]
        globalLock.lock()
        try {
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
        } finally {
            to.amount += amount
        }
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