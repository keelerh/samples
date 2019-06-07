package com.example.braid
import com.example.flow.TestFlow
import io.bluebank.braid.corda.BraidConfig
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

/**
 * A regular Corda service that bootstraps the Braid server when the node
 * starts.
 *
 * The Braid server offers a user-defined set of flows and services.
 *
 * @property serviceHub the node's `AppServiceHub`.
 */
@CordaService
class BootstrapBraidService(val serviceHub: AppServiceHub) : SingletonSerializeAsToken() {
    init {

        val braidHandler: Handler<AsyncResult<String>> = Handler { asyncResult -> System.out.println(asyncResult.result()) }

        val config = serviceHub.getAppContext().config
        val port = if(config.exists("braidPort")) config.getInt("braidPort") else 8081

        BraidConfig()
                // Include a flow on the Braid server.
                //.withFlow("DealFlow", DealFlow.Dealer::class.java)
                //.withFlow("CreateGameFlow", CreateGameFlow.Dealer::class.java)
                .withFlow("TestFlow", TestFlow.Initiator::class.java)
                // Include a service on the Braid server.
                .withService("service", BraidService(serviceHub))
                // The port the Braid server listens on.
                .withPort(port)
                // Using http instead of https.
                .withHttpServerOptions(HttpServerOptions().setSsl(false))
                // Start the Braid server.
                .bootstrapBraid(serviceHub, braidHandler)
    }
}