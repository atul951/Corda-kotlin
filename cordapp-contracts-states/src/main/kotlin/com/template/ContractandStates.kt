package com.template

import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder

//Contract code
val ATUL_CONTRACT_ID = "com.template.AtulContract"

class AtulContract : Contract {

    interface Commands : CommandData{
        class Issue : Commands
        class Move : Commands
    }

    override fun verify(tx: LedgerTransaction) {

        val groups = tx.groupStates(AtulState::withoutOwner)

        val command = tx.commands.requireSingleCommand<AtulContract.Commands>()

        val timeWindow: TimeWindow? = tx.timeWindow

        for ((inputs, outputs, _) in groups){
            when(command.value){
                is AtulContract.Commands.Move -> {
                    val input = inputs.single()
                    requireThat {
                        "The transaction is signed by the owner of Atul." using (input.owner.owningKey in command.signers)
                        "The state is propagated." using (outputs.size == 1)
                    }
                }
                is AtulContract.Commands.Issue -> {
                    val output = outputs.single()
                    timeWindow?.untilTime ?: throw IllegalArgumentException("Issuances must be timestamped")
                    requireThat {
                        "output states are issued by a command signer." using (output.issuer.owningKey in command.signers)
                        "Output contains a thought." using (!output.thought.equals(""))
                        "Can`t reissue an existing state." using inputs.isEmpty()
                    }
                }
                else -> throw IllegalArgumentException("Unrecognised command")
            }
        }

    }
    companion object {
        fun generateIssue(thought: String, issuer: AbstractParty, owner: AbstractParty, notary: Party): TransactionBuilder{
            val state = AtulState(thought, issuer, owner)
            val stateAndContract = StateAndContract(state, ATUL_CONTRACT_ID)
            return TransactionBuilder(notary = notary).withItems(stateAndContract,Command(Commands.Issue(),issuer.owningKey))
        }

        fun generateMove(tx: TransactionBuilder, Atul: StateAndRef<AtulState>, newOwner: AbstractParty){
            tx.addInputState(Atul)
            val outputState = Atul.state.data.withOwner(newOwner)
            tx.addOutputState(outputState, ATUL_CONTRACT_ID)
            tx.addCommand(Command(Commands.Move(),Atul.state.data.owner.owningKey))
        }
    }
}

//State
data class AtulState(val thought:String, val issuer: AbstractParty, val owner: AbstractParty): ContractState{
    override val participants: List<AbstractParty> get() = listOf(owner, issuer)

    fun withoutOwner() = copy(owner = AnonymousParty(NullKeys.NullPublicKey))

    fun withOwner(newOwner: AbstractParty): AtulState{
        return copy(owner=newOwner)
    }
}