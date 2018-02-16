package com.template

import co.paralleluniverse.fibers.Suspendable
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val CORP_NAME = CordaX500Name(organisation = "BCS LEARNING", locality = "Sydney", country = "AU")
internal val NOTARY_NAME = CordaX500Name(organisation = "Turicum notery service", locality = "Zurich", country = "CH")
internal val B_NAME = CordaX500Name(organisation = "Bank of Atul", locality = "Delhi", country = "India")

private var whitelistedIssuers : MutableSet<CordaX500Name> = mutableSetOf()

//api endpoints
@Path("template")
class TemplateApi(val rpcOps: CordaRPCOps){

    @GET
    @Path("templateEndpoint")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok("Template get endpoint").build()
    }
}

private fun getIssuerWhitelist(serviceHub: ServiceHub): Set<PublicKey> {
    if (whitelistedIssuers.isEmpty()) {
        val tempSet : MutableSet<CordaX500Name> = mutableSetOf()
        val conf = ConfigFactory.parseResources("application.conf")
        conf.getObjectList("whitelist.atul_issuers").flatMapTo(tempSet, { x ->
            val cn = x.get("common_name")


            val parsedName: CordaX500Name = when(cn) {
                null -> CordaX500Name(
                        x.get("organization")?.unwrapped() as String? ?: "",
                        x.get("locality")?.unwrapped() as String? ?: "",
                        x.get("country")?.unwrapped() as String? ?: ""
                )
                else -> CordaX500Name(
                        cn.unwrapped() as String? ?: "",
                        x.get("organization")?.unwrapped() as String? ?: "",
                        x.get("locality")?.unwrapped() as String? ?: "",
                        x.get("country")?.unwrapped() as String? ?: ""
                )
            }
            listOf(parsedName)
        })
        whitelistedIssuers = tempSet
    }

    val outs: MutableSet<PublicKey> = mutableSetOf()
    whitelistedIssuers.flatMapTo(outs, { x->
        val key = serviceHub.identityService.wellKnownPartyFromX500Name(x)?.owningKey
        if (key!= null){
            listOf(key)
        }else{
            emptyList()
        }
    })
    return outs
}

//Flow
@InitiatingFlow
@StartableByRPC
class AtulIssueRequest(val thought: String, val issure: Party): FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.getNotary(NOTARY_NAME)
                ?: throw FlowException("could not find the trusted Turicom Notary node.")

        val selfID = serviceHub.myInfo.legalIdentities[0]

        val issueTxBuilder = AtulContract.generateIssue(thought,issure,selfID,notary)

        val bankSession = initiateFlow(issure)

        issueTxBuilder.setTimeWindow(
                TimeWindow.fromStartAndDuration(Instant.now(serviceHub.clock), Duration.ofMillis(10000))
        )

        // Verifying the transaction
        issueTxBuilder.verify(serviceHub)

        //Signing the transaction
        val signedTx = serviceHub.signInitialTransaction(issueTxBuilder)

        //Obtaining the counterparty`s signature
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(bankSession),
                CollectSignaturesFlow.tracker()))

        //Finalising the transaction
        return subFlow(FinalityFlow(fullySignedTx))
    }

}

@InitiatedBy(AtulIssueRequest::class)
class AtulIssueResponse(val counterpartySession: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(
                counterpartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction)= requireThat {
                val whitelistedIssuers = getIssuerWhitelist(serviceHub)
                val output = stx.tx.outputs.single().data
                "This must be a Atul Transaction." using(output is AtulState)
                val Atul = output as AtulState
                "I must be a whitelisted node" using (whitelistedIssuers.contains(ourIdentity.owningKey))
                "This Atul must be issued by a whitelisted node" using
                        ( whitelistedIssuers.contains(Atul.issuer.owningKey))
                "The issuer of Atul must be issuing node" using
                        (Atul.issuer.owningKey == ourIdentity.owningKey)
            }
        }
        subFlow(signTransactionFlow)
    }
}

@InitiatingFlow
@StartableByRPC
class AtulMoveRequest(val Atul: StateAndRef<AtulState>, val newOwner: Party): FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.getNotary(NOTARY_NAME) ?:
                throw FlowException("Could not find Turicum Notary node.")

        val txBuilder = TransactionBuilder(notary=notary)
        AtulContract.generateMove(txBuilder,Atul,newOwner)

        val moveSession = initiateFlow(newOwner)

        txBuilder.setTimeWindow(TimeWindow.fromStartAndDuration(Instant.now(serviceHub.clock), Duration.ofMillis(10000)))

        //verifying the transaction.
        txBuilder.verify(serviceHub)

        //Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        //Obtaining the counterparty`s signature
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(moveSession),CollectSignaturesFlow.tracker()))

        //Finalising the transaction
        return subFlow(FinalityFlow(fullySignedTx))
    }
}

@InitiatedBy(AtulMoveRequest::class)
class AtulMoveResponse(val counterpartySession: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(counterpartySession,SignTransactionFlow.tracker()){
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val whitelistedIssuers = getIssuerWhitelist(serviceHub)
                val output = stx.tx.outputs.single().data
                "This must be Atul Transaction." using (output is AtulState)
                val Atul = output as AtulState
                "The Atul must be issued by a whitelisted node" using (whitelistedIssuers.contains(Atul.issuer.owningKey))
                "The issuer of Atul must be a issuing node" using (Atul.issuer.owningKey == ourIdentity.owningKey)
            }
        }
        subFlow(signTransactionFlow)
    }
}
/*
//Plugins
class TemplateWebPlugin : WebServerPluginRegistry{

    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<out Any>> = listOf(Function(::TemplateApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/template
            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )

}
*/
//Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist{
    override val whitelist : List<Class<*>> = listOf()
}