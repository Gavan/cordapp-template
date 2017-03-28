package com.clinicalclaims.contract

import com.clinicalclaims.model.Claim
import com.clinicalclaims.state.ClaimState
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.by
import net.corda.core.crypto.SecureHash

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [ClaimState], which in turn encapsulates an [Claim].
 *
 * For a new [Claim] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [Claim].
 * - An Create() command with the public keys of both the sender and the recipient.
 *
 * All contracts must sub-class the [Contract] interface.
 */
open class ClaimContract : Contract {
    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: TransactionForContract) {
        //group states by everything except status
        val groups = tx.groupStates(ClaimState::linearId)
        val command = tx.commands.requireSingleCommand<Commands>()

        for ((inputs, outputs, key) in groups) {
            when (command.value) {

            /**
             * Create Command constraints
             */
                is Commands.Create -> {
                    val output = outputs.single()
                    requireThat {
                        // Generic constraints around the claim transaction.
                        "No inputs should be consumed when issuing an Claim." by (inputs.isEmpty())
                        "Only one output state should be created." by (outputs.size == 1)

                        "The sender and the recipient cannot be the same entity." by (output.clinic != output.insurer)
                        "All of the participants must be signers." by (command.signers.containsAll(output.participants))

                        // Claim specific constraints.
                        "The Claim's cost must be non-negative." by (output.claim.cost > 0)
                        "The Claim's status should be pending." by (output.status == "Pending")
                    }
                }

                /**
                 * Approve Command constraints
                 */
                is Commands.Approve -> {
                    val input = inputs.single()
                    val output = outputs.single()
                    requireThat {
                        "There should only be 1 input claim" by (inputs.size ==1)
                        "There should only be 1 output claim" by (outputs.size == 1)
                        "Input claim status should be Pending" by (input.status == "Pending")
                        "Output claim status should be Approved" by (output.status == "Approved")
                        "Transaction should be signed by insurer" by (input.insurer.owningKey in command.signers)
                    }
                }

                /**
                 * Reject Command constraints
                 */
                is Commands.Reject -> {
                    val input = inputs.single()
                    val output = outputs.single()
                    requireThat {
                        "There should only be 1 input claim" by (inputs.size ==1)
                        "There should only be 1 output claim" by (outputs.size == 1)
                        "Input claim status should be Pending" by (input.status == "Pending")
                        "Output claim status should be Rejected" by (output.status == "Rejected")
                        "Transaction should be signed by insurer" by (input.insurer.owningKey in command.signers)
                    }
                }

                else -> throw IllegalArgumentException("Unrecognized Command")
            }
        }
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Approve : TypeOnlyCommandData(), Commands
        class Reject : TypeOnlyCommandData(), Commands
    }

    /** This is a reference to the underlying legal contract template and associated parameters. */
    override val legalContractReference: SecureHash = SecureHash.sha256("Claim Legal Contract")
}
