@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.ReentrantLock

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    override fun getAmount(id: Int): Long {
        val account = accounts[id]
        account.lock.lock()
        return try {
            account.amount
        } finally {
            account.lock.unlock()
        }
    }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        account.lock.lock()
        return try {
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        } finally {
            account.lock.unlock()
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        val account = accounts[id]
        account.lock.lock()
        return try {
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        } finally {
            account.lock.unlock()
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        // TODO: Make this operation thread-safe via fine-grained locking.
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        val from = accounts[fromId]
        val to = accounts[toId]

//        val orderedAccounts = if (fromId < toId)  arrayOf(from, to) else arrayOf(to, from)
        val orderedAccounts = arrayOf(from, to)

        orderedAccounts[0].lock.lock()
        try {
            orderedAccounts[1].lock.lock()
            try {
                check(amount <= from.amount) { "Underflow" }
                check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
                from.amount -= amount
                to.amount += amount
            } finally {
                orderedAccounts[1].lock.unlock()
            }
        } finally {
            orderedAccounts[0].lock.unlock()
        }
    }

    class Account {
        var amount: Long = 0
        val lock = ReentrantLock()
    }
}