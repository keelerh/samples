package com.example.server

import com.example.flow.DealFlow.Dealer
import com.example.state.Card
import com.example.state.CardState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

/**
 *  A Spring Boot Server API controller for interacting with the node via RPC.
 */

@RestController
@RequestMapping("/api/example/") // The paths for GET and POST requests are relative to this base path.
class MainController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myLegalName = rpc.proxy.nodeInfo().legalIdentities.first().name
    private val proxy = rpc.proxy

    /**
     * Returns the node's name.
     */
    @GetMapping(value = [ "me" ], produces = [ APPLICATION_JSON_VALUE ])
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the network map service. These names can be used
     * to look up identities using the identity service.
     */
    @GetMapping(value = [ "peers" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = proxy.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                // Filter out myself, notary and eventual network map started by driver.
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all Card states that exist in the node's vault.
     */
    @GetMapping(value = [ "cards" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getCards() : ResponseEntity<List<StateAndRef<CardState>>> {
        return ResponseEntity.ok(proxy.vaultQueryBy<CardState>().states)
    }

    /**
     * Initiates a flow to agree a dealt card between the dealer and a player.
     *
     * Once the flow finishes it will have written the card to the ledger. Both the dealer and
     * the player will be able to see it when calling /spring/api/cards on their respective nodes.
     *
     * This end-point takes a Player name parameter as part of the path. If the serving node
     * can't find the other player in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method
     * returns.
     */
    @PostMapping(value = [ "create-card" ], produces = [ TEXT_PLAIN_VALUE ],
            headers = [ "Content-Type=application/x-www-form-urlencoded" ])
    fun createCard(request: HttpServletRequest): ResponseEntity<String> {
        val cardSuit = request.getParameter("cardSuit")
                ?: return ResponseEntity.badRequest().body(
                        "Query parameter 'cardSuit' must not be null.\n")
        val cardRank = request.getParameter("cardRank")
                ?: return ResponseEntity.badRequest().body(
                        "Query parameter 'cardRank' must not be null.\n")
        val playerName = request.getParameter("playerName")
                ?: return ResponseEntity.badRequest().body(
                        "Query parameter 'playerName' must not be null.\n")
        val gameId = request.getParameter("gameId").toInt()
        if (gameId <= 0 ) {
            return ResponseEntity.badRequest().body(
                    "Query parameter 'gameId' must not be non-negative.\n")
        }
        val playerX500Name = CordaX500Name.parse(playerName)
        val player = proxy.wellKnownPartyFromX500Name(playerX500Name)
                ?: return ResponseEntity.badRequest().body(
                        "Player named $playerName cannot be found.\n")

        return try {
            val signedTxs = proxy.startTrackedFlow(
                    ::Dealer, listOf(Card(Card.Suit.valueOf(cardSuit), Card.Rank.valueOf(cardRank))), listOf(player), gameId)
                    .returnValue.getOrThrow()
            ResponseEntity.status(HttpStatus.CREATED).body(
                    "Transaction id ${signedTxs[0].id} committed to ledger.\n")
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            ResponseEntity.badRequest().body(ex.message!!)
        }
    }

    /**
     * Displays all cards states that this node has been dealt.
     */
    @GetMapping(value = [ "my-cards" ], produces = [ APPLICATION_JSON_VALUE ])
    fun getMyCards(): ResponseEntity<List<StateAndRef<CardState>>>  {
        val myCards = proxy.vaultQueryBy<CardState>().states.filter {
            it.state.data.player == proxy.nodeInfo().legalIdentities.first() }
        return ResponseEntity.ok(myCards)
    }
}
