package com.example.client

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.RPCException
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.seconds
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException

class RpcClient(config: RpcClientConfig) {

    companion object {
        private val log = contextLogger()
    }

    private val partyToConnection = config.partyToRpcPort.map { (party, networkHostAndPort) ->
        val nodeAddress = NetworkHostAndPort.parse(networkHostAndPort)
        val connection = establishConnectionWithRetry(nodeAddress)
        Pair(party, connection)
    }.toMap().toMutableMap()

    private val connections = partyToConnection.values

    fun getConnection(party: String): CordaRPCConnection {
        return partyToConnection.getValue(party)
    }

    fun closeAllConnections() {
        connections.forEach {
            try {
                it.close()
            } catch (e: Exception) {
                log.warn("An error occurred while closing the connection: ${e.message}", e)
            }
        }
    }

    private fun establishConnectionWithRetry(nodeAddress: NetworkHostAndPort): CordaRPCConnection {
        do {
            val connection = try {
                log.info("Connecting to: $nodeAddress")
                val client = CordaRPCClient(nodeAddress)
                val _connection = client.start("user1", "test")
                // Check connection is truly operational before returning it.
                val nodeInfo = _connection.proxy.nodeInfo()
                require(nodeInfo.legalIdentitiesAndCerts.isNotEmpty())
                _connection
            } catch (secEx: ActiveMQSecurityException) {
                // Happens when incorrect credentials provided - no point to retry connecting.
                throw secEx
            } catch (ex: RPCException) {
                // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                log.info("Exception upon establishing connection: " + ex.message)
                null
            } catch (ex: ActiveMQConnectionTimedOutException) {
                // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                log.info("Exception upon establishing connection: " + ex.message)
                null
            } catch (ex: ActiveMQUnBlockedException) {
                // Deliberately not logging full stack trace as it will be full of internal stacktraces.
                log.info("Exception upon establishing connection: " + ex.message)
                null
            }

            if (connection != null) {
                log.info("Connection successfully established with: $nodeAddress")
                return connection
            }
            // Could not connect this time round - pause before giving another try.
            Thread.sleep(5.seconds.toMillis())
        } while (connection == null)

        throw Exception("Could not connect")
    }
}

data class RpcClientConfig(val partyToRpcPort: Map<String, String>)