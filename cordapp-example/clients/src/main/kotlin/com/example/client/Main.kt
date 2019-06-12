package com.example.client

import com.example.flow.CreateGameFlow
import com.example.flow.DealFlow
import com.example.state.Card
import com.example.state.CardState
import com.example.state.Deck
import com.example.state.GameState
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import java.util.logging.Logger

val log: Logger = Logger.getLogger("root")

fun main(args: Array<String>) {
    val config = RpcClientConfig(hashMapOf(
            "Dealer" to "localhost:10005",
            "PlayerA" to "localhost:10009",
            "PlayerB" to "localhost:10013",
            "Notary" to "localhost:20001")
    )
    val client = RpcClient(config)
    playGame(client)
    client.closeAllConnections()
}

fun playGame(client: RpcClient) {
    log.info("Creating game.")
    val nodeConn = client.getConnection("Dealer")
    nodeConn.proxy.startFlowDynamic(
            CreateGameFlow.Dealer::class.java, getPlayers(client)).returnValue.getOrThrow()

    val gameState = queryForGameState(nodeConn)
    val cards = cardsToDeal(gameState.players.size * 2)
    nodeConn.proxy.startFlowDynamic(
            DealFlow.Dealer::class.java, cards, gameState.players, gameState.gameId).returnValue.getOrThrow()
    logDealtCards(nodeConn)
}

fun getPlayers(client: RpcClient): List<Party?> {
    val playerA = client.getConnection("PlayerA").proxy.wellKnownPartyFromX500Name(CordaX500Name("PlayerA", "New York", "US"))
    val playerB = client.getConnection("PlayerB").proxy.wellKnownPartyFromX500Name(CordaX500Name("PlayerB", "Paris", "FR"))
    return listOf(playerA, playerB)
}

fun cardsToDeal(numCardsToDeal: Int): List<Card> {
    val deck = Deck()
    log.info("Shuffling cards.")
    deck.shuffle()
    log.info(String.format("Dealing %d cards.", numCardsToDeal))
    return deck.dealXCards(numCardsToDeal)
}

fun queryForGameState(nodeConn: CordaRPCConnection): GameState {
    val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
    val gameStateResults = nodeConn.proxy.vaultQueryByCriteria(queryCriteria, GameState::class.java)
    val gameState = gameStateResults.states[0].state.data
    log.info(String.format("Game %d created.", gameState.gameId))
    return gameState
}

fun logDealtCards(nodeConn: CordaRPCConnection) {
    val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
    val cardStateResults = nodeConn.proxy.vaultQueryByCriteria(queryCriteria, CardState::class.java)
    val totalCardsDealt = cardStateResults.states.size
    val firstCard = cardStateResults.states[totalCardsDealt - 4].state.data.card
    val secondCard = cardStateResults.states[totalCardsDealt - 3].state.data.card
    val thirdCard = cardStateResults.states[totalCardsDealt - 2].state.data.card
    val fourthCard = cardStateResults.states[totalCardsDealt - 1].state.data.card
    log.info(String.format("Dealt cards: %s, %s, %s, %s.",
            firstCard, secondCard, thirdCard, fourthCard))
}
