package com.example.test.flow

import com.example.flow.DealFlow
import com.example.state.Card
import com.example.state.CardState
import junit.framework.Assert.assertEquals
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class DealFlowTests {
    companion object {
        @JvmStatic val TEST_CARDS = listOf(
                Card(Card.Suit.HEARTS, Card.Rank.FIVE),
                Card(Card.Suit.SPADES, Card.Rank.ACE),
                Card(Card.Suit.DIAMONDS, Card.Rank.TWO),
                Card(Card.Suit.SPADES, Card.Rank.NINE))
        @JvmStatic val TEST_GAME_ID = 1
    }

    private lateinit var network: MockNetwork
    private lateinit var dealer: StartedMockNode
    private lateinit var playerA: StartedMockNode
    private lateinit var playerB: StartedMockNode
    private lateinit var testPlayers: List<Party>

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.example.contract"),
                TestCordapp.findCordapp("com.example.flow")
        )))
        dealer = network.createPartyNode()
        playerA = network.createPartyNode()
        playerB = network.createPartyNode()
        testPlayers = listOf(playerA.info.singleIdentity(), playerB.info.singleIdentity())
        listOf(dealer, playerA, playerB).forEach { it.registerInitiatedFlow(
                DealFlow.Player::class.java) }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the dealer`() {
        val flow = DealFlow.Dealer(TEST_CARDS, testPlayers, TEST_GAME_ID)
        val future = dealer.startFlow(flow)
        network.runNetwork()

        val signedTxs = future.getOrThrow()
        val dealerOwningKey = dealer.info.singleIdentity().owningKey
        signedTxs.forEach { tx -> tx.verifySignaturesExcept(dealerOwningKey) }
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the player`() {
        val flow = DealFlow.Dealer(TEST_CARDS, testPlayers, TEST_GAME_ID)
        val future = dealer.startFlow(flow)
        network.runNetwork()

        val signedTxs = future.getOrThrow()

        val playerAOwningKey = playerA.info.singleIdentity().owningKey
        signedTxs.subList(0, 1).forEach { tx -> tx.verifySignaturesExcept(playerAOwningKey) }

        val playerBOwningKey = playerB.info.singleIdentity().owningKey
        signedTxs.subList(2, 3).forEach { tx -> tx.verifySignaturesExcept(playerBOwningKey) }
    }

    @Test
    fun `flow records a transaction in both parties' transaction storages`() {
        val flow = DealFlow.Dealer(TEST_CARDS, testPlayers, TEST_GAME_ID)
        val future = dealer.startFlow(flow)
        network.runNetwork()

        val signedTxs = future.getOrThrow()

        // Check transactions are in proper storage.
        for (node in listOf(dealer, playerA)) {
            signedTxs.subList(0, 1).forEach {tx ->
                node.services.validatedTransactions.getTransaction(tx.id) }
        }
        for (node in listOf(dealer, playerB)) {
            signedTxs.subList(2, 3).forEach { tx ->
                node.services.validatedTransactions.getTransaction(tx.id)
            }
        }
    }

    @Test
    fun `recorded transaction has no inputs and a single output`() {
        val flow = DealFlow.Dealer(TEST_CARDS, testPlayers, TEST_GAME_ID)
        val future = dealer.startFlow(flow)
        network.runNetwork()

        val signedTxs = future.getOrThrow()

        // We check the recorded transaction is in all relevant vaults.
        for (i in 0..1) {
            for (node in listOf(dealer, playerA)) {
                val recordedTx = node.services.validatedTransactions.getTransaction(signedTxs[i].id)
                val txOutputs = recordedTx!!.tx.outputs
                assert(txOutputs.size == 1)

                val recordedState = txOutputs[0].data as CardState
                assertEquals(recordedState.card, TEST_CARDS[i])
                assertEquals(recordedState.dealer, dealer.info.singleIdentity())
                assertEquals(recordedState.player, playerA.info.singleIdentity())
                assertEquals(recordedState.gameId, TEST_GAME_ID)
            }
        }

        for (i in 2..3) {
            for (node in listOf(dealer, playerB)) {
                val recordedTx = node.services.validatedTransactions.getTransaction(signedTxs[i].id)
                val txOutputs = recordedTx!!.tx.outputs
                assert(txOutputs.size == 1)

                val recordedState = txOutputs[0].data as CardState
                assertEquals(recordedState.card, TEST_CARDS[i])
                assertEquals(recordedState.dealer, dealer.info.singleIdentity())
                assertEquals(recordedState.player, playerB.info.singleIdentity())
                assertEquals(recordedState.gameId, TEST_GAME_ID)
            }
        }
    }

    @Test
    fun `flow records the correct Cards in all parties' vaults`() {
        val flow = DealFlow.Dealer(TEST_CARDS, testPlayers, TEST_GAME_ID)
        val future = dealer.startFlow(flow)
        network.runNetwork()

        future.getOrThrow()

        // We check the recorded Cards are in all vaults.
        for (i in 0..1) {
            dealer.transaction {
                val cards = dealer.services.vaultService.queryBy<CardState>().states
                assertEquals(4, cards.size)
                val recordedState = cards[i].state.data
                assertEquals(recordedState.card, TEST_CARDS[i])
                assertEquals(recordedState.dealer, dealer.info.singleIdentity())
                assertEquals(recordedState.player, playerA.info.singleIdentity())
            }
            playerA.transaction {
                val cards = playerA.services.vaultService.queryBy<CardState>().states
                assertEquals(2, cards.size)
                val recordedState = cards[i].state.data
                assertEquals(recordedState.card, TEST_CARDS[i])
                assertEquals(recordedState.dealer, dealer.info.singleIdentity())
                assertEquals(recordedState.player, playerA.info.singleIdentity())
            }
        }

        for (i in 2..3) {
            dealer.transaction {
                val cards = dealer.services.vaultService.queryBy<CardState>().states
                assertEquals(4, cards.size)
                val recordedState = cards[i].state.data
                assertEquals(recordedState.card, TEST_CARDS[i])
                assertEquals(recordedState.dealer, dealer.info.singleIdentity())
                assertEquals(recordedState.player, playerB.info.singleIdentity())
            }
            playerB.transaction {
                val cards = playerB.services.vaultService.queryBy<CardState>().states
                assertEquals(2, cards.size)
                val recordedState = cards[i-2].state.data
                assertEquals(recordedState.card, TEST_CARDS[i])
                assertEquals(recordedState.dealer, dealer.info.singleIdentity())
                assertEquals(recordedState.player, playerB.info.singleIdentity())
            }
        }
    }
}