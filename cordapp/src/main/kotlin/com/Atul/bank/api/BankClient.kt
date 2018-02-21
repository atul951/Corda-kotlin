package com.Atul.bank.api

import com.Atul.bank.AtulIssueRequest
import com.Atul.bank.AtulMoveRequest
import com.Atul.bank.AtulState
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.http.HttpApi

/**
 * Interface for communicating with the Bank of Daniel node.
 */
object BankOfAtulClientApi {
    /**
     * Requests a Daniel issuance via RPC.
     *
     * @return the issuing transaction.
     */
    // TODO: privileged security controls required
    fun requestRPCIssue(rpcAddress: NetworkHostAndPort, thought: String, issuer: CordaX500Name): SignedTransaction {
        val client = CordaRPCClient(rpcAddress)
        client.start("user1", "test").use { connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()

            val issuerID = rpc.wellKnownPartyFromX500Name(issuer) ?: throw IllegalArgumentException("Could not find the issuer node '${issuer}'.")

            return rpc.startFlow(::AtulIssueRequest, thought, issuerID)
                    .returnValue.getOrThrow()
        }
    }

    /**
     * Requests a Daniel transfer via RPC
     *
     * @return the move transaction
     */
    // TODO: privileged security controls required
    fun requestRPCMove(rpcAddress: NetworkHostAndPort, atul: StateAndRef<AtulState>, newOwner: CordaX500Name): SignedTransaction {
        val client = CordaRPCClient(rpcAddress)
        client.start("user1", "test").use { connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()

            val ownerID = rpc.wellKnownPartyFromX500Name(newOwner) ?: throw IllegalArgumentException("Could not find the new owner node '${newOwner}'.")

            return rpc.startFlow(::AtulMoveRequest, atul, ownerID)
                    .returnValue.getOrThrow()
        }
    }
}