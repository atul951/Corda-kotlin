package com.Atul.bank

import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.time.Instant

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val ATUL_CONTRACT_ID = "com.Atul.bank.AtulContract"

class AtulContract : Contract {
    interface Commands : CommandData {
        class Issue : Commands
        class Move : Commands
        //class Exit: Commands TODO: Implement exits
    }

    override fun verify(tx: LedgerTransaction) {
        // Group by everything except owner: any modification to the Atul at all is considered changing it fundamentally.
        // Grouping lets us handle multiple states (of the same type) in one transaction.
        val groups = tx.groupStates(AtulState::withoutOwner)

        val command = tx.commands.requireSingleCommand<Commands>()

        val timeWindow: TimeWindow? = tx.timeWindow

        for ((inputs, outputs, _) in groups) {
            when (command.value) {
                is Commands.Move -> {
                    val input = inputs.single()
                    requireThat {
                        "The transaction is signed by the owner of the Atul." using (input.owner.owningKey in command.signers)
                        "The state is propagated." using (outputs.size == 1)
                        // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                        // the input ignoring the owner field due to the grouping.
                    }
                }

                is Commands.Issue -> {
                    val output = outputs.single()
                    timeWindow?.untilTime ?: throw IllegalArgumentException("Issuances must be timestamped")
                    requireThat {
                        // Don't allow people to issue commercial paper under other entities identities.
                        "Output states are issued by a command signer." using (output.issuer.owningKey in command.signers)
                        "Output contains a thought." using (!output.thought.equals(""))
                        "Can't reissue an existing state." using inputs.isEmpty()
                    }
                }

                else -> throw IllegalArgumentException("Unrecognised command")
            }
        }
    }

    companion object {
        /*
         * Genreates an issuance of a Atul.
         */
        fun generateIssue(thought: String, issuer: AbstractParty, owner: AbstractParty,
                          notary: Party): TransactionBuilder {
            val state = AtulState(thought, issuer, owner)
            val stateAndContract = StateAndContract(state, ATUL_CONTRACT_ID)
            return TransactionBuilder(notary = notary).withItems(stateAndContract, Command(Commands.Issue(), issuer.owningKey))
        }

        /*
         * Generates a Move command from an existing Daniel to a new owner.
         */
        fun generateMove(tx: TransactionBuilder, atul: StateAndRef<AtulState>, newOwner: AbstractParty) {
            tx.addInputState(atul)
            val outputState = atul.state.data.withOwner(newOwner)
            tx.addOutputState(outputState, ATUL_CONTRACT_ID)
            tx.addCommand(Command(Commands.Move(), atul.state.data.owner.owningKey))
        }
    }
}

// *********
// * State *
// *********
data class AtulState(
        val thought: String,
        val issuer: AbstractParty,
        val owner: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> get() = listOf(owner, issuer)

    fun withoutOwner() = copy(owner = AnonymousParty(NullKeys.NullPublicKey))

    fun withOwner(newOwner: AbstractParty): AtulState {
        return copy(owner=newOwner)
    }
}