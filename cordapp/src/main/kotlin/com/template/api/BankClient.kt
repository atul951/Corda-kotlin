package com.template.api

import com.template.AtulIssueRequest
import com.template.AtulMoveRequest
import com.template.AtulState
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import java.lang.IllegalArgumentException

object BankOfAtulBankApi{

    fun requestRPCIssue(rpcAddress: NetworkHostAndPort, thought: String, issuer: CordaX500Name): SignedTransaction{
        val client = CordaRPCClient(rpcAddress)
        client.start("user1","test").use { connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()

            val issuerID = rpc.wellKnownPartyFromX500Name(issuer)
             ?: throw IllegalArgumentException("Could not find the issuer node '${issuer}'.")

            return rpc.startFlow(::AtulIssueRequest, thought, issuerID).returnValue.getOrThrow()
        }
    }

    fun requestRPCMove(rpcAddress: NetworkHostAndPort, Atul: StateAndRef<AtulState>, newOwner: CordaX500Name): SignedTransaction{
        val client = CordaRPCClient(rpcAddress)
        client.start("user1","test").use{ connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()

            val ownerID = rpc.wellKnownPartyFromX500Name(newOwner) ?:
                    throw IllegalArgumentException("Could not find the new owner node '${newOwner}'.")

            return rpc.startFlow(::AtulMoveRequest, Atul, ownerID).returnValue.getOrThrow()
        }
    }

}