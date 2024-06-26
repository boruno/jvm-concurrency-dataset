@file:Suppress("DuplicatedCode")

package day1

import day1.Bank.Companion.MAX_AMOUNT
import java.util.concurrent.locks.*

class FineGrainedBank(accountsNumber: Int) : Bank {
    private val accounts: Array<Account> = Array(accountsNumber) { Account() }

    private fun <T> withLocked(accId: Int, action: (Account) -> T): T {
        return withLocked(listOf(accId)) { action(it[0]) }
    }

    private fun <T> withLocked(accId1: Int, accId2: Int, action: (Account, Account) -> T): T {
        return withLocked(listOf(accId1, accId2)) { action(it[0], it[1]) }
    }

    private fun <T> withLocked(accIds: List<Int>, action: (List<Account>) -> T): T {
        val sortedAccIds = accIds.sorted()
        val sortedAccs = accIds.map { accounts[it] }
        sortedAccs.forEach { it.lock.lock() }
        try {
            val accs = List(accIds.size) { sortedAccs[sortedAccIds.indexOf(accIds[it])] }
            return action(accs)
        } finally {
            sortedAccs.asReversed().forEach { it.lock.unlock() }
        }
    }

    override fun getAmount(id: Int): Long {
        return withLocked(id) { it.amount }
    }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        return withLocked(id) { account ->
            check(!(amount > MAX_AMOUNT || account.amount + amount > MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        return withLocked(id) { account ->
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        return withLocked(fromId, toId) { from, to ->
            check(amount <= from.amount) { "Underflow" }
            check(!(amount > MAX_AMOUNT || to.amount + amount > MAX_AMOUNT)) { "Overflow" }
            from.amount -= amount
            to.amount += amount
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

        val lock = ReentrantLock()
    }
}