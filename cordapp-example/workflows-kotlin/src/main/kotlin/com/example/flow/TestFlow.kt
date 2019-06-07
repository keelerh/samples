package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.CardContract
import com.example.flow.DealFlow.Dealer
import com.example.flow.DealFlow.Player
import com.example.flow.helpers.ProgressTracker
import com.example.flow.helpers.ProgressTracker.tracker
import com.example.state.Card
import com.example.state.CardState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 *
 * NOTE: All methods called within the [FlowLogic] sub-class need to be annotated with the
 * @Suspendable annotation.
 */
object TestFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator() : FlowLogic<Int>() {
        override val progressTracker = tracker()

        private var counter = 0

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): Int {
            // Obtain a reference to the notary we want to use.
            return 0
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
