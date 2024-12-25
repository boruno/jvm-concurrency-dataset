@file:Suppress("DuplicatedCode")

//package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }



    override fun getAmount(id: Int): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        accounts[id].lock.lock()
        try {
            return accounts[id].amount
        } finally {
            accounts[id].lock.unlock()
        }
//        val account = accounts[id]
//        return account.amount
    }

    override fun deposit(id: Int, amount: Long): Long {
        // TODO: Make this operation thread-safe via fine-grained locking.
        accounts[id].lock.lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            return account.amount
        } finally {
            accounts[id].lock.unlock()
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        accounts[id].lock.lock()
        try {
            // TODO: Make this operation thread-safe via fine-grained locking.
            require(amount > 0) { "Invalid amount: $amount" }
            val account = accounts[id]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            return account.amount
        } finally {
            accounts[id].lock.unlock()
        }

    }

    private val transferLocks: Map<Pair<Int, Int>, ReentrantLock> = HashMap<Pair<Int, Int>, ReentrantLock>()

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        if (fromId == toId) return
        val pair = Pair(fromId, toId)
        val lock = transferLocks.getOrDefault(pair, ReentrantLock())
        lock.lock()
        try {
            require(amount > 0) { "Invalid amount: $amount" }
            val fromAccount = accounts[fromId]
            val toAccount = accounts[toId]
            check(fromAccount.amount - amount >= 0) { "Underflow" }
            check(!(amount > MAX_AMOUNT || toAccount.amount + amount > MAX_AMOUNT)) { "Overflow" }
            fromAccount.amount -= amount
            toAccount.amount += amount
        } finally {
            lock.unlock()
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

        /**
         * TODO: use this mutex to protect the account data.
         */
        val lock = ReentrantLock()
    }
}