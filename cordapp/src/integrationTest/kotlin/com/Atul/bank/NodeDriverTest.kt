package com.Atul.bank

import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultTrackBy
import net.corda.core.node.services.Vault
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.driver.driver
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.parallel
import org.junit.Test
import kotlin.test.assertEquals

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to using deployNodes)
 * Do not use in a production environment.
 *
 * To debug your CorDapp:
 *
 * 1. Run the "Run Template CorDapp" run configuration.
 * 2. Wait for all the nodes to start.
 * 3. Note the debug ports for each node, which should be output to the console. The "Debug CorDapp" configuration runs
 *    with port 5007, which should be "PartyA". In any case, double-check the console output to be sure.
 * 4. Set your breakpoints in your CorDapp code.
 * 5. Run the "Debug CorDapp" remote debug run configuration.
 */
class NodeTest {

    @Test
    fun `run general tests`() {
        val user = User("user1", "test", permissions = setOf())
        val privilegedUser = User("user1", "test", permissions = setOf(
                startFlowPermission<AtulIssueRequest>()
        ))

        driver(isDebug = true) {
            val (notary, nodeBank, nodeBCS) = listOf(
                    startNode(providedName = CordaX500Name("Turicum Notary Service", "Zurich", "CH"), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type))),
                    startNode(providedName = CordaX500Name("BCS Learning", "Sydney", "AU"), rpcUsers = listOf(user)),
                    startNode(providedName = CordaX500Name("Bank of Atul", "Delhi", "IN"), rpcUsers = listOf(privilegedUser))).map { it.getOrThrow() }

            val bcsClient = nodeBCS.rpcClientToNode()
            val bcsRPC = bcsClient.start("user1", "test").proxy
            val bodClient = nodeBank.rpcClientToNode()
            val bodRPC = bodClient.start("user1", "test").proxy

            bcsRPC.waitUntilNetworkReady().getOrThrow()
            bodRPC.waitUntilNetworkReady().getOrThrow()

            val bodVaultUpdates = bodRPC.vaultTrackBy<AtulState>().updates
            val bcsVaultUpdates = bcsRPC.vaultTrackBy<AtulState>().updates

            val notaryParty = bcsRPC.notaryIdentities().first()
            (1..10).map { i ->
                bcsRPC.startFlow(AtulIssueRequest, i.toString()).returnValue
            }.transpose().getOrThrow()

            /*
            (1..10).map { i ->
                bcsRPC.startFlow(::DanielMoveRequest, nodeBank.nodeInfo.chooseIdentity()).returnValue
            }.transpose().getOrThrow()
            */



            bodVaultUpdates.expectEvents {
                parallel(
                        (1..10).map { i ->
                            expect { update: Vault.Update<AtulState> ->
                                assertEquals(i.toString(), update.produced.first().state.data.thought)
                            }
                        }
                )
            }
        }
    }
}