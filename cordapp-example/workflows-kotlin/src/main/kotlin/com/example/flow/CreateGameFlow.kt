package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.GameContract
import com.example.flow.CreateGameFlow.Player
import com.example.flow.CreateGameFlow.Dealer
import com.example.flow.helpers.ProgressTracker
import com.example.flow.helpers.ProgressTracker.tracker
import com.example.state.GameState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * This flow allows parties in a poker game (the [Dealer] and some number of [Player]s) to
 * come to an agreement about a poke game encapsulated within a [GameState].
 *
 * NOTE: All methods called within the [FlowLogic] sub-class need to be annotated with the
 * @Suspendable annotation.
 */
object CreateGameFlow {
    @InitiatingFlow
    @StartableByRPC
    @StartableByService
    class Dealer(private val players: List<Party>) : FlowLogic<SignedTransaction>() {
        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = ProgressTracker.GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val gameState = GameState(
                    listOf(),
                    serviceHub.myInfo.legalIdentities.first(),
                    players,
                    Random().nextInt(Integer.MAX_VALUE),
                    mapOf())
            val txCommand = Command(
                    GameContract.Commands.Create(), gameState.participants.map { it.owningKey })

            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(gameState, GameContract.ID)
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

            val sessions = mutableListOf<FlowSession>()

            for (player in players) {
                // Send the state to the counterparty, and receive it back with their signature.
                val otherPartySession = initiateFlow(player)
                sessions.add(otherPartySession)
            }
            val fullySignedTx = subFlow(CollectSignaturesFlow(
                    partSignedTx, sessions, ProgressTracker.GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = ProgressTracker.FINALISING_TRANSACTION

            logger.info("Initiating game {}.", gameState.gameId)

            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(
                    fullySignedTx,
                    sessions,
                    ProgressTracker.FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Dealer::class)
    class Player(val playerSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(playerSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be a CreateGame transaction." using (output is GameState)
                    val gameState = output as GameState
                    logger.info("Player {} received game state {}.", playerSession, gameState)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(playerSession, expectedTxId = txId))
        }
    }
}
