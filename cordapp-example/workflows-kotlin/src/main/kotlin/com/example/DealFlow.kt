package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.CardContract
import com.example.flow.DealFlow.Acceptor
import com.example.flow.DealFlow.Initiator
import com.example.state.Card
import com.example.state.CardState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [IOUState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
object DealFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val cards: List<Card>,
                    val players: List<Party>,
                    val gameId: Int) : FlowLogic<List<SignedTransaction>>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new cards.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        var counter = 0;

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): List<SignedTransaction> {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            val txs = mutableListOf<SignedTransaction>()

            for (player in players) {
                for (i in 0..1) {
                    // Stage 1.
                    progressTracker.currentStep = GENERATING_TRANSACTION
                    // Generate an unsigned transaction.
                    val card = cards.get(counter)
                    val cardState = CardState(card, serviceHub.myInfo.legalIdentities.first(), player, gameId)
                    val txCommand = Command(CardContract.Commands.Create(), cardState.participants.map { it.owningKey })

                    val txBuilder = TransactionBuilder(notary)
                            .addOutputState(cardState, CardContract.ID)
                            .addCommand(txCommand)

                    // Stage 2.
                    progressTracker.currentStep = VERIFYING_TRANSACTION
                    // Verify that the transaction is valid.
                    txBuilder.verify(serviceHub)

                    // Stage 3.
                    progressTracker.currentStep = SIGNING_TRANSACTION
                    // Sign the transaction.
                    val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

                    // Stage 4.
                    progressTracker.currentStep = GATHERING_SIGS
                    // Send the state to the counterparty, and receive it back with their signature.
                    val otherPartySession = initiateFlow(player)
                    val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

                    // Stage 5.
                    progressTracker.currentStep = FINALISING_TRANSACTION

                    logger.warn("Dealing card {} to player {}.", card, player)

                    // Notarise and record the transaction in both parties' vaults.
                    val tx = subFlow(FinalityFlow(fullySignedTx, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))
                    txs.add(tx)

                    counter++
                }
            }
            return txs
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Deal transaction." using (output is CardState)
                    val cardState = output as CardState
                    logger.warn("Player {} received card {}.", otherPartySession, cardState.card)
                    "I won't accept invalid cards." using (cardState.card.isValid())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
