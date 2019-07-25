package com.example.test.flow

import com.example.flow.ExampleFlow
import com.example.flow.ExampleUpdateFlow
import com.example.schema.IOUSchemaV1
import com.example.state.Card
import com.example.state.IOUState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import net.corda.core.node.services.vault.Builder.lessThanOrEqual
import java.util.*
import kotlin.test.assertEquals

class ExampleFlowTest {
    private lateinit var network: MockNetwork
    private lateinit var dealer: StartedMockNode
    private lateinit var playerA: StartedMockNode
    private lateinit var playerB: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.example.contract"),
                TestCordapp.findCordapp("com.example.flow")
        )))
        dealer = network.createPartyNode()
        playerA = network.createPartyNode()
        playerB = network.createPartyNode()
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(dealer, playerA, playerB).forEach { it.registerInitiatedFlow(ExampleFlow.Acceptor::class.java) }
        listOf(dealer, playerA, playerB).forEach { it.registerInitiatedFlow(ExampleUpdateFlow.Acceptor::class.java) }
        network.runNetwork()
    }


    @After
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `Vault StateStatus All`() {

        val flow = ExampleFlow.Initiator(0, playerA.info.singleIdentity())
        val future = dealer.startFlow(flow)
        network.runNetwork()

        future.getOrThrow()

        var linearId: UUID

        (1..4).forEach {
            linearId = playerA.services.vaultService.queryBy<IOUState>(criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)).states.first().state.data.linearId.id
            val flow = ExampleUpdateFlow.Initiator(linearId,it, playerA.info.singleIdentity())
            val future = dealer.startFlow(flow)
            network.runNetwork()

            future.getOrThrow()
        }

        var resultAll = playerA.services.vaultService.queryBy<IOUState>(criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)).states
        println(resultAll)
        assertEquals(resultAll.size, 5)

        var resultAllFiltered = playerA.services.vaultService.queryBy<IOUState>(criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL).and(QueryCriteria.VaultCustomQueryCriteria(IOUSchemaV1.PersistentIOU::value.lessThanOrEqual(5)))).states
        println(resultAllFiltered)
        assertEquals(resultAllFiltered.size, 1)

    }

}