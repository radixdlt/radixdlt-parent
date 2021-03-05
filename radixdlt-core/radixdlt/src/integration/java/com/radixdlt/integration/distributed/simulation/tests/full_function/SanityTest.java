/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.simulation.tests.full_function;

import com.google.inject.AbstractModule;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.ConsensusMonitors;
import com.radixdlt.integration.distributed.simulation.LedgerMonitors;
import com.radixdlt.integration.distributed.simulation.NetworkLatencies;
import com.radixdlt.integration.distributed.simulation.NetworkOrdering;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.SimulationTest.Builder;
import com.radixdlt.integration.distributed.simulation.application.RadixEngineUniqueGenerator;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.sync.SyncConfig;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SanityTest {
	private final Builder bftTestBuilder = SimulationTest.builder()
		.numNodes(4)
		.networkModules(
			NetworkOrdering.inOrder(),
			NetworkLatencies.fixed()
		)
		.fullFunctionNodes(View.of(10), SyncConfig.of(200L, 10, 5000L))
		.addNodeModule(new AbstractModule() {
			@Override
			protected void configure() {
				bindConstant().annotatedWith(MempoolThrottleMs.class).to(10L);
				bindConstant().annotatedWith(MempoolMaxSize.class).to(1000);
			}
		})
		.addTestModules(
			ConsensusMonitors.safety(),
			ConsensusMonitors.liveness(1, TimeUnit.SECONDS),
			ConsensusMonitors.noTimeouts(),
			ConsensusMonitors.directParents(),
			LedgerMonitors.consensusToLedger(),
			LedgerMonitors.ordered()
		)
		.addMempoolSubmissionsSteadyState(new RadixEngineUniqueGenerator())
		.addMempoolCommittedChecker();

	@Test
	public void sanity_tests_should_pass() {
		SimulationTest simulationTest = bftTestBuilder
			.build();

		final var results = simulationTest.run().awaitCompletion();
		assertThat(results).allSatisfy((name, err) -> AssertionsForClassTypes.assertThat(err).isEmpty());
	}
}
