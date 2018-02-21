package com.Atul.bank.server

import com.Atul.bank.AtulIssueRequest
import com.Atul.bank.AtulMoveRequest
import com.Atul.bank.AtulState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.CordaX500Name.Companion.parse
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.base64ToByteArray
import net.corda.core.utilities.base64toBase58
import net.corda.core.utilities.toBase64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import javax.servlet.http.HttpServletRequest


private const val CONTROLLER_NAME = "config.controller.name"

/**
 *  A controller for interacting with the node via RPC.
 */
@RestController
//@CrossOrigin(origins="*")
@RequestMapping("/akbank") // The paths for GET and POST requests are relative to this base path.
private class RestController(
        private val rpc: NodeRPCConnection,
        @Value("\${config.rpc.port}") val rpcPort: Int,
        @Value("\${${CONTROLLER_NAME}}") private val controllerName: String) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myName = rpc.proxy.nodeInfo().legalIdentities.first().name

    @GetMapping("/date", produces=[MediaType.APPLICATION_JSON_VALUE])
    fun getCurrentDate(): Any {
        return mapOf("date" to LocalDateTime.now().toLocalDate())
    }

    @GetMapping("/port", produces=[MediaType.TEXT_PLAIN_VALUE])
    fun getPort(): String {
        return rpcPort.toString()
    }

    /**
     *  Request asset issuance
     */
    @PostMapping("/issue-asset-request", consumes=[MediaType.APPLICATION_JSON_VALUE])
    fun issueAssetRequest(@RequestBody params: IssueParams): ResponseEntity<String> {
        //val issuerX500 = CordaX500Name.parse(params.issuer) // We should definitely write a jackson thing to process json into a name, but for now this'll work.
        val issuerX500 = params.issuer
        return try {
            val proxy = rpc.proxy
            val issuerID = proxy.wellKnownPartyFromX500Name(issuerX500) ?: throw IllegalArgumentException("Could not find the issuer node '$issuerX500'.")
            proxy.startFlow(::AtulIssueRequest, params.thought, issuerID).returnValue.getOrThrow()
            logger.info("Issue request completed successfully: $params.thought")
            ResponseEntity.status(HttpStatus.CREATED).build()
        } catch (e: Exception) {
            logger.error("Issue request failed", e)
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    /**
     *  Request asset move
     */
    @PostMapping("/issue-move-request", produces=[], consumes=[MediaType.APPLICATION_JSON_VALUE])
    fun issueMoveRequest(@RequestBody params: MoveParams): ResponseEntity<String> {
        //val newOwnerX500 = CordaX500Name.parse(params.newOwner)
        val newOwnerX500 = params.newOwner
        val atul = StateRef(SecureHash.SHA256(params.atulHash.base64ToByteArray()), params.atulIndex)
        return try {
            val proxy = rpc.proxy
            val sar = proxy.vaultQueryBy<AtulState>(QueryCriteria.VaultQueryCriteria(stateRefs = listOf(atul))).states.getOrNull(0) ?: throw IllegalArgumentException("Could not find state")
            val issuerID = proxy.wellKnownPartyFromX500Name(newOwnerX500) ?: throw IllegalArgumentException("Could not find the new owner node '$newOwnerX500'.")
            proxy.startFlow(::AtulMoveRequest, sar, issuerID).returnValue.getOrThrow()
            logger.info("Movement request completed successfully")
            ResponseEntity.status(HttpStatus.CREATED).build()
        } catch (e: Exception) {
            logger.error("Movement request failed", e)
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    /** Returns the node's name. */
    @GetMapping("/myname", produces = ["text/plain"])
    private fun myName() = myName.toString()

    /** Returns a list of the node's network peers. */
    @GetMapping("/peersnames", produces = ["application/json"])
    private fun peersNames(): Map<String, List<String>> {
        val nodes = rpc.proxy.networkMapSnapshot()
        val nodeNames = nodes.map { it.legalIdentities.first().name }
        val filteredNodeNames = nodeNames.filter { it.organisation !in listOf(controllerName, myName) }
        val filteredNodeNamesToStr = filteredNodeNames.map { it.toString() }
        return mapOf("peers" to filteredNodeNamesToStr)
    }

    /** Returns a list of existing DanielState's. */
    @GetMapping("/getatuls", produces = ["application/json"])
    private fun getAtuls(): List<Map<String, Any>> {
        val atulStateAndRefs = rpc.proxy.vaultQueryBy<AtulState>().states
        val atulStates = atulStateAndRefs.map { it }
        return atulStates.map { mapOf(
                "issuer" to it.state.data.issuer.toString(),
                "owner" to it.state.data.owner.toString(),
                "thought" to it.state.data.thought,
                "hash" to it.ref.txhash.bytes.toBase64(),
                "index" to it.ref.index) }
    }
}

private data class IssueParams(val thought: String, val issuer: CordaX500Name)
private data class MoveParams(val atulHash: String, val atulIndex: Int, val newOwner: CordaX500Name)
