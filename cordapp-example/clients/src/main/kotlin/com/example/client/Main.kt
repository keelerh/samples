package com.example.client

import com.example.flow.CreateGameFlow
import com.example.flow.DealFlow
import com.example.state.CardState
import com.example.state.Deck
import com.example.state.GameState
import net.corda.core.identity.CordaX500Name
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
    val nodeConn = client.getConnection("Dealer")
    val playerA = client.getConnection("PlayerA").proxy.wellKnownPartyFromX500Name(CordaX500Name("PlayerA", "New York", "US"))
    val playerB = client.getConnection("PlayerB").proxy.wellKnownPartyFromX500Name(CordaX500Name("PlayerB", "Paris", "FR"))
    val players = listOf(playerA, playerB)

    log.info("Creating game.")
    nodeConn.proxy.startFlowDynamic(
            CreateGameFlow.Dealer::class.java, players).returnValue.getOrThrow()

    val queryCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
    val gameStateResults = nodeConn.proxy.vaultQueryByCriteria(queryCriteria, GameState::class.java)
    val gameState = gameStateResults.states[0].state.data
    log.info(String.format("Game %d created.", gameState.gameId))

    log.info("Shuffling cards.")
    val deck = Deck()
    deck.shuffle()
    val numCardsToDeal = gameState.players.size * 2
    val cards = deck.dealXCards(numCardsToDeal)

    log.info(String.format("Dealing %d cards.", numCardsToDeal))
    nodeConn.proxy.startFlowDynamic(
            DealFlow.Dealer::class.java, cards, gameState.players, gameState.gameId).returnValue.getOrThrow()

    val cardStateResults = nodeConn.proxy.vaultQueryByCriteria(queryCriteria, CardState::class.java)
    val firstCard = cardStateResults.states[0].state.data.card
    val secondCard = cardStateResults.states[1].state.data.card
    val thirdCard = cardStateResults.states[2].state.data.card
    val fourthCard = cardStateResults.states[3].state.data.card
    log.info(String.format("Dealt cards: %s, %s, %s, %s.",
            firstCard, secondCard, thirdCard, fourthCard))
}
