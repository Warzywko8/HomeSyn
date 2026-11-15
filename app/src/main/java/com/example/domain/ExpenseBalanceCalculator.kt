package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.data.model.Expense
import com.example.data.model.Household
import com.example.data.model.SettlementTransfer
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

@Immutable
data class BalanceSummary(
    val totalCycleGrosze: Long,
    val transfers: List<SettlementTransfer>,
    val primaryHeadlinePl: String,
    val isSettled: Boolean
)

object ExpenseBalanceCalculator {

    private val polishLocale = Locale.forLanguageTag("pl-PL")

    fun formatPln(grosze: Long): String {
        val formatter = NumberFormat.getNumberInstance(polishLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "${formatter.format(grosze / 100.0)} zł"
    }

    fun parsePlnToGrosze(input: String): Long? {
        val normalized = input.trim()
            .replace("zł", "", ignoreCase = true)
            .replace("pln", "", ignoreCase = true)
            .replace("\\s+".toRegex(), "")
            .replace(",", ".")
        val value = normalized.toDoubleOrNull() ?: return null
        if (value <= 0.0 || value > 1_000_000.0) return null
        return (value * 100.0).roundToLong().takeIf { it > 0L }
    }

    fun calculate(
        household: Household,
        activeCycleExpenses: List<Expense>,
        useCustomWeights: Boolean = false
    ): BalanceSummary {
        val memberUids = household.memberUids.distinct()
        if (memberUids.size < 2 || activeCycleExpenses.isEmpty()) {
            val total = activeCycleExpenses.sumOf { it.amountGrosze }
            return BalanceSummary(
                totalCycleGrosze = total,
                transfers = emptyList(),
                primaryHeadlinePl = if (memberUids.size < 2) {
                    "Zaproś drugą osobę, aby dzielić wydatki"
                } else {
                    "Wszystko wyrównane (0,00 zł)"
                },
                isSettled = activeCycleExpenses.isEmpty()
            )
        }

        val totalGrosze = activeCycleExpenses.sumOf { it.amountGrosze }
        val paidByMember = mutableMapOf<String, Long>().withDefault { 0L }
        for (expense in activeCycleExpenses) {
            if (expense.paidByUid in memberUids) {
                paidByMember[expense.paidByUid] = paidByMember.getValue(expense.paidByUid) + expense.amountGrosze
            }
        }

        val weights: Map<String, Double> = if (useCustomWeights) {
            memberUids.associateWith { uid -> (household.members[uid]?.weight ?: 1.0).coerceAtLeast(0.1) }
        } else {
            memberUids.associateWith { 1.0 }
        }
        val totalWeight = weights.values.sum().coerceAtLeast(1.0)

        // Compute fair share in integer grosze with deterministic remainder distribution
        val shares = mutableMapOf<String, Long>()
        var assignedTotal = 0L
        memberUids.forEachIndexed { idx, uid ->
            val share = if (idx == memberUids.lastIndex) {
                totalGrosze - assignedTotal
            } else {
                val exact = (totalGrosze.toDouble() * (weights[uid] ?: 1.0)) / totalWeight
                exact.roundToLong()
            }
            shares[uid] = share
            assignedTotal += share
        }

        // Net > 0 means member should RECEIVE money; Net < 0 means member OWES money
        data class BalanceNode(val uid: String, val name: String, var amount: Long)

        val creditors = mutableListOf<BalanceNode>()
        val debtors = mutableListOf<BalanceNode>()

        for (uid in memberUids) {
            val net = paidByMember.getValue(uid) - (shares[uid] ?: 0L)
            val name = household.memberName(uid)
            if (net > 1L) {
                creditors.add(BalanceNode(uid, name, net))
            } else if (net < -1L) {
                debtors.add(BalanceNode(uid, name, abs(net)))
            }
        }

        creditors.sortByDescending { it.amount }
        debtors.sortByDescending { it.amount }

        val transfers = mutableListOf<SettlementTransfer>()
        var cIdx = 0
        var dIdx = 0
        while (cIdx < creditors.size && dIdx < debtors.size) {
            val creditor = creditors[cIdx]
            val debtor = debtors[dIdx]
            val settle = min(creditor.amount, debtor.amount)
            if (settle > 0L) {
                transfers.add(
                    SettlementTransfer(
                        fromUid = debtor.uid,
                        fromName = debtor.name,
                        toUid = creditor.uid,
                        toName = creditor.name,
                        amountGrosze = settle
                    )
                )
            }
            creditor.amount -= settle
            debtor.amount -= settle
            if (creditor.amount <= 1L) cIdx++
            if (debtor.amount <= 1L) dIdx++
        }

        val headline = when {
            transfers.isEmpty() -> "Wszystko wyrównane (0,00 zł)"
            transfers.size == 1 -> {
                val t = transfers.first()
                "${t.fromName} jest winien/winna ${t.toName}: ${formatPln(t.amountGrosze)}"
            }
            else -> {
                val first = transfers.first()
                "${first.fromName} → ${first.toName}: ${formatPln(first.amountGrosze)} (+${transfers.size - 1})"
            }
        }

        return BalanceSummary(
            totalCycleGrosze = totalGrosze,
            transfers = transfers,
            primaryHeadlinePl = headline,
            isSettled = transfers.isEmpty()
        )
    }
}
