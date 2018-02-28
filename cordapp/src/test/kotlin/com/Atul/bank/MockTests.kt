package com.Atul.bank

import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.nodeapi.internal.ServiceType
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ResolveTransactionsFlowTest {
    lateinit var mockNet: MockNetwork
    lateinit var corp: StartedNode<MockNetwork.MockNode>
    lateinit var invalidIssuer: StartedNode<MockNetwork.MockNode>
    lateinit var bod: StartedNode<MockNetwork.MockNode>
    lateinit var notary: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.Atul.bank")
        mockNet = MockNetwork()
        notary = mockNet.createNotaryNode(legalName= NOTARY_NAME)
        bod = mockNet.createPartyNode(legalName= BOD_NAME, networkMapAddress=notary.network.myAddress)
        corp = mockNet.createPartyNode(legalName= CORP_NAME, networkMapAddress=notary.network.myAddress)
        invalidIssuer = mockNet.createPartyNode(legalName=CordaX500Name("Thunder Entertainment", "Irvine", "US"), networkMapAddress=notary.network.myAddress)
        mockNet.registerIdentities()
        bod.internals.registerInitiatedFlow(AtulIssueResponse::class.java)
        bod.internals.registerInitiatedFlow(AtulMoveResponse::class.java)
        corp.internals.registerInitiatedFlow(AtulMoveResponse::class.java)
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `simple issuance flow`() {
        val p = AtulIssueRequest("TEST THOUGHT", bod.info.chooseIdentity())
        val future = corp.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        val results = future.getOrThrow()
        val ds = results.tx.outputStates[0] as AtulState
        assertEquals("TEST THOUGHT", ds.thought)
    }

    @Test
    fun `invalid issuer`() {
        val p = AtulIssueRequest("TEST THOUGHT", invalidIssuer.info.chooseIdentity())
        try {
            val future = corp.services.startFlow(p).resultFuture
            mockNet.runNetwork()
            future.getOrThrow()
        } catch (e: FlowException) {
            assertEquals("java.lang.IllegalArgumentException: Failed requirement: I must be a whitelisted node", e.originalMessage)
            return
        }
        fail()
    }

    @Test
    fun `simple issue-and-move flow`() {
        val p = AtulIssueRequest("IF WORK IS ENERGY EXPENDED OVER TIME WHY DON'T WE GET PAID IN CALORIES", bod.info.chooseIdentity())
        val future = corp.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        val results = future.getOrThrow()
        val ds = results.tx.outputStates[0] as AtulState
        assertEquals("IF WORK IS ENERGY EXPENDED OVER TIME WHY DON'T WE GET PAID IN CALORIES", ds.thought)

        val move = AtulMoveRequest(results.tx.outRef(0), bod.info.chooseIdentity())
        val moveFuture = corp.services.startFlow(move).resultFuture
        mockNet.runNetwork()
        val moveResults = moveFuture.getOrThrow()
        val mds = moveResults.tx.outputStates[0] as AtulState
        assertEquals(bod.info.chooseIdentity(), mds.owner)
    }

    @Test
    fun `transaction is stored in both parties transaction storage`() {
        val request = AtulIssueRequest("FOR NAUGHT BUT A THOUGHT", bod.info.chooseIdentity())
        val future = corp.services.startFlow(request).resultFuture
        mockNet.runNetwork()
        val signedTx = future.getOrThrow()

        assertEquals(signedTx, corp.services.validatedTransactions.getTransaction(signedTx.id))
        assertEquals(signedTx, bod.services.validatedTransactions.getTransaction(signedTx.id))
    }

    @Test
    fun `correct DanielState recorded in vault`() {
        val request = AtulIssueRequest("FOR NAUGHT BUT A THOUGHT", bod.info.chooseIdentity())
        val future = corp.services.startFlow(request).resultFuture
        mockNet.runNetwork()
        future.getOrThrow()

        for(node in listOf(corp, bod)) {
            node.database.transaction {
                val states = node.services.vaultService.queryBy<AtulState>().states
                assertEquals(1, states.size)
                val recordedState = states.single().state.data
                assertEquals("FOR NAUGHT BUT A THOUGHT", recordedState.thought)
                assertEquals(corp.info.chooseIdentity(), recordedState.owner)
                assertEquals(bod.info.chooseIdentity(), recordedState.issuer)
            }
        }
    }
}
