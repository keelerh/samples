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
 * This flow allows two parties (the [Dealer] and a [Player]) to come to an agreement about the
 * two initial cards dealt to the player, represented by two [CardState]s.
 *
 * The [Player] always accepts a valid set of cards.
 *
 * NOTE: All methods called within the [FlowLogic] sub-class need to be annotated with the
 * @Suspendable annotation.
 */
object DealFlow {
    @InitiatingFlow
    @StartableByRPC
    class Dealer(private val cards: List<Card>,
                 private val players: List<Party>,
                 private val gameId: Int) : FlowLogic<List<SignedTransaction>>() {
        override val progressTracker = tracker()

        private var counter = 0

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): List<SignedTransaction> {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            val txs = mutableListOf<SignedTransaction>()

            for (player in players) {
                // Dealing two cards to each player.
                for (i in 0..1) {
                    // Stage 1.
                    progressTracker.currentStep = ProgressTracker.GENERATING_TRANSACTION
                    // Generate an unsigned transaction.
                    val card = cards[counter]
                    val dealer = serviceHub.myInfo.legalIdentities.first()
                    val cardState = CardState(card, dealer, player, gameId)
                    val txCommand = Command(CardContract.Commands.Create(),
                            cardState.participants.map { it.owningKey })

                    val txBuilder = TransactionBuilder(notary)
                            .addOutputState(cardState, CardContract.ID)
                            .addCommand(txCommand)

                    // Stage 2.
                    progressTracker.currentStep = ProgressTracker.VERIFYING_TRANSACTION
                    // Verify that the transaction is valid.
                    txBuilder.verify(serviceHub)

                    // Stage 3.
                    progressTracker.currentStep = ProgressTracker.SIGNING_TRANSACTION
                    // Sign the transaction.
                    val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

                    // Stage 4.
                    progressTracker.currentStep = ProgressTracker.GATHERING_SIGS
                    // Send the state to the counterparty, and receive it back with their signature.
                    val otherPartySession = initiateFlow(player)
                    val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx,
                            setOf(otherPartySession), ProgressTracker.GATHERING_SIGS.childProgressTracker()))

                    // Stage 5.
                    progressTracker.currentStep = ProgressTracker.FINALISING_TRANSACTION

                    logger.info("Dealing card {} to player {}.", card, player)

                    // Notarise and record the transaction in both parties' vaults.
                    val tx = subFlow(FinalityFlow(fullySignedTx, setOf(otherPartySession),
                            ProgressTracker.FINALISING_TRANSACTION.childProgressTracker()))
                    txs.add(tx)

                    counter++
                }
            }
            return txs
        }
    }

    @InitiatedBy(Dealer::class)
    class Player(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a Deal transaction." using (output is CardState)
                    val cardState = output as CardState
                    logger.info("Player {} received card {}.", otherPartySession, cardState.card)
                    "I won't accept invalid cards." using (cardState.card.isValid())
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}
