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
 *
 */

package com.radixdlt.integration.statemachine;

import com.google.common.base.Stopwatch;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.system.NextValidatorSetEvent;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UpdateValidatorFee;
import com.radixdlt.atom.actions.UpdateValidatorOwner;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class LargeEpochChangeTest {
	private static final Logger logger = LogManager.getLogger();
	private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new MainnetForkConfigsModule(),
			new RadixEngineForksLatestOnlyModule(
				new RERulesConfig(
					FeeTable.create(
						Amount.ofMicroTokens(200), // 0.0002XRD per byte fee
						Amount.ofTokens(1000) // 1000XRD per resource
					),
					OptionalInt.of(50), // 50 Txns per round
					10_000,
					1,
					Amount.ofTokens(100), // Minimum stake
					150, // Two weeks worth of epochs
					Amount.ofTokens(10), // Rewards per proposal
					9800, // 98.00% threshold for completed proposals to get any rewards
					100 // 100 max validators
				)
			),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY),
			new MockedGenesisModule(
				Set.of(TEST_KEY.getPublicKey()),
				Amount.ofTokens(100000),
				Amount.ofTokens(1000)
			),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	@Test
	public void large_epoch() throws Exception {
		var rt = Runtime.getRuntime();
		logger.info("max mem: {}MB", rt.maxMemory() / 1024 / 1024);

		int privKeyStart = 2;
		int numTxnsPerRound = 10;
		int numRounds = 10000;

		createInjector().injectMembers(this);
		// Arrange
		var request = TxnConstructionRequest.create();
		IntStream.range(privKeyStart, numRounds * numTxnsPerRound + privKeyStart)
			.forEach(i -> {
				var k = PrivateKeys.ofNumeric(i);
				var addr = REAddr.ofPubKeyAccount(k.getPublicKey());
				request.action(new MintToken(REAddr.ofNativeToken(), addr, Amount.ofTokens(numRounds * 1000).toSubunits()));
			});
		var mint = sut.construct(request).buildWithoutSignature();
		logger.info("mint_txn_size={}", mint.getPayload().length);
		var accumulator = new AccumulatorState(2, HashUtils.zero256());
		var proof = new LedgerProof(HashUtils.zero256(), LedgerHeader.create(1, View.of(1), accumulator, 0), new TimestampedECDSASignatures());
		sut.execute(List.of(mint), LedgerAndBFTProof.create(proof), PermissionLevel.SYSTEM);

		var systemConstruction = Stopwatch.createUnstarted();
		var construction = Stopwatch.createUnstarted();
		var signatures = Stopwatch.createUnstarted();
		var execution = Stopwatch.createUnstarted();

		var feesPaid = UInt256.ZERO;

		for (int round = 1; round <= 10000; round++) {
			if (round % 1000 == 0) {
				logger.info(
					"Staking txn {}/{} sys_construct_time: {}s user_construct_time: {}s sig_time: {}s execute_time: {}s",
					round * (numTxnsPerRound + 1),
					numRounds * (numTxnsPerRound + 1),
					systemConstruction.elapsed(TimeUnit.SECONDS),
					construction.elapsed(TimeUnit.SECONDS),
					signatures.elapsed(TimeUnit.SECONDS),
					execution.elapsed(TimeUnit.SECONDS)
				);
			}
			var txns = new ArrayList<Txn>();
			systemConstruction.start();
			var sysTxn = sut.construct(new NextRound(round, false, 1, v -> TEST_KEY.getPublicKey()))
				.buildWithoutSignature();
			systemConstruction.stop();
			txns.add(sysTxn);
			for (int i = 0; i < numTxnsPerRound; i++) {
				var privateKey = PrivateKeys.ofNumeric((round - 1) * numTxnsPerRound + i + privKeyStart);
				var pubKey = privateKey.getPublicKey();
				var addr = REAddr.ofPubKeyAccount(privateKey.getPublicKey());
				construction.start();
				var builder = sut.construct(
					TxnConstructionRequest.create()
						.feePayer(addr)
						.action(new StakeTokens(addr, pubKey, Amount.ofTokens(100 + i).toSubunits()))
						.action(new RegisterValidator(pubKey))
						.action(new UpdateValidatorFee(pubKey, 100))
						.action(new UpdateValidatorOwner(pubKey, REAddr.ofPubKeyAccount(TEST_KEY.getPublicKey())))
				);
				construction.stop();
				signatures.start();
				var txn = builder.signAndBuild(privateKey::sign);
				signatures.stop();
				txns.add(txn);
			}

			var acc = new AccumulatorState(2 + round * (numTxnsPerRound + 1), HashUtils.zero256());
			var proof2 = new LedgerProof(HashUtils.zero256(), LedgerHeader.create(1, View.of(1), acc, 0), new TimestampedECDSASignatures());
			execution.start();
			var result = sut.execute(txns, LedgerAndBFTProof.create(proof2), PermissionLevel.SUPER_USER);
			execution.stop();
			for (var p : result.getProcessedTxns()) {
				feesPaid = feesPaid.add(p.getFeePaid());
			}
		}
		logger.info("total_fees_paid: {}", Amount.ofSubunits(feesPaid));

		// Act
		construction.reset();
		construction.start();
		logger.info("constructing epoch...");
		var txn = sut.construct(new NextEpoch(1)).buildWithoutSignature();
		construction.stop();
		logger.info("epoch_construction: size={}MB time={}s", txn.getPayload().length / 1024 / 1024, construction.elapsed(TimeUnit.SECONDS));

		construction.reset();
		construction.start();
		logger.info("preparing epoch...");
		var result = sut.transientBranch().execute(List.of(txn), PermissionLevel.SUPER_USER);
		sut.deleteBranches();
		var nextValidatorSet = result.getProcessedTxn().getEvents().stream()
			.filter(NextValidatorSetEvent.class::isInstance)
			.map(NextValidatorSetEvent.class::cast)
			.findFirst()
			.map(e -> BFTValidatorSet.from(
				e.nextValidators().stream()
					.map(v -> BFTValidator.from(BFTNode.create(v.getValidatorKey()), v.getAmount())))
			);
		var stateUpdates = result.getProcessedTxn().stateUpdates().count();
		construction.stop();
		logger.info(
			"epoch_preparation: state_updates={} verification_time={}s store_time={}s total_time={}s",
			stateUpdates,
			result.getVerificationTime() / 1000,
			result.getStoreTime() / 1000,
			construction.elapsed(TimeUnit.SECONDS)
		);
		construction.reset();
		construction.start();
		logger.info("executing epoch...");
		var acc = new AccumulatorState(2 + 1 + numRounds * (1 + numTxnsPerRound), HashUtils.zero256());
		var header = LedgerHeader.create(1, View.of(10), acc, 0, nextValidatorSet.orElseThrow());
		var proof2 = new LedgerProof(HashUtils.zero256(), header, new TimestampedECDSASignatures());
		var executionResult = this.sut.execute(List.of(txn), LedgerAndBFTProof.create(proof2), PermissionLevel.SUPER_USER);
		construction.stop();
		logger.info(
			"epoch_execution: verification_time={}s store_time={}s total_time={}s",
			executionResult.getVerificationTime() / 1000,
			executionResult.getStoreTime() / 1000,
			construction.elapsed(TimeUnit.SECONDS)
		);
		for (var v : nextValidatorSet.orElseThrow().getValidators()) {
			logger.info("validator {} {}", v.getNode(), v.getPower());
		}
	}
}
