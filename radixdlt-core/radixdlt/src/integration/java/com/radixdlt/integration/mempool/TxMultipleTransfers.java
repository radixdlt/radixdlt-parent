package com.radixdlt.integration.mempool;

import com.google.inject.*;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.client.ArchiveApiModule;
import com.radixdlt.client.service.TransactionStatusService;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.berkeley.BerkeleyClientApiStore;
import com.radixdlt.client.store.berkeley.ScheduledQueueFlush;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.integration.staking.DeterministicRunner;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.radixdlt.client.api.TransactionStatus.*;
import static org.junit.Assert.*;

public class TxMultipleTransfers {


	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Named("RadixEngine")
	private HashSigner hashSigner;
	@Inject
	@Self
	private ECPublicKey self;
	@Inject
	private DeterministicRunner runner;
	@Inject
	private EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher;
	@Inject
	private EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;
	@Inject
	private TransactionStatusService transactionStatusService;
	@Inject
	private BerkeleyClientApiStore berkeleyClientApiStore;
	@Inject
	private ScheduledEventDispatcher<ScheduledQueueFlush> scheduledFlushEventDispatcher;
	@Inject
	private ClientApiStore clientApiStore;

	public TxMultipleTransfers() {
	}

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(2000, 10),
			new BetanetForksModule(),
			new RadixEngineForksLatestOnlyModule(View.of(100), false),
			RadixEngineConfig.asModule(1, 10, 1000),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new MempoolFillerModule(),
			new ArchiveApiModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(ClientApiStore.class).to(BerkeleyClientApiStore.class).in(Scopes.SINGLETON);
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}

				@Singleton
				@Provides
				private RuntimeProperties runtimeProperties() throws ParseException {
					return new RuntimeProperties(new JSONObject(), new String[0]);
				}

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance(@Self ECPublicKey self) {
					return TokenIssuance.of(self, TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.from(10000)));
				}
			}
		);
	}

	public REProcessedTxn waitForCommit() {
		var mempoolAdd = runner.runNextEventsThrough(MempoolAddSuccess.class);
		var committed = runner.runNextEventsThrough(
			TxnsCommittedToLedger.class,
			c -> c.getParsedTxs().stream().anyMatch(txn -> txn.getTxn().getId().equals(mempoolAdd.getTxn().getId()))
		);

		return committed.getParsedTxs().stream()
			.filter(t -> t.getTxn().getId().equals(mempoolAdd.getTxn().getId()))
			.findFirst()
			.orElseThrow();
	}

	public REProcessedTxn dispatchAndWaitForCommit(TxAction action) {
		nodeApplicationRequestEventDispatcher.dispatch(NodeApplicationRequest.create(action));
		return waitForCommit();
	}

	@Test
	public void multiTransfer() throws Exception {
		createInjector().injectMembers(this);
		runner.start();

		var initialAddr = REAddr.ofPubKeyAccount(self);
		var start = 1;
		var end = 100;
		List<Integer> rangeOfInts = IntStream.rangeClosed(start, end).boxed().collect(Collectors.toList());
		List<REAddr> rangeOfAddr = IntStream.rangeClosed(start, rangeOfInts.size())
			.mapToObj(i -> REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey()))
			.collect(Collectors.toList());

		List<TransferToken> transferTokenList = rangeOfInts.stream().
			map(i -> new TransferToken(REAddr.ofNativeToken(), initialAddr, rangeOfAddr.get(i - 1),
			TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.from(rangeOfInts.get(i - 1))))).collect(Collectors.toList());

		List<REProcessedTxn> processedTxns = transferTokenList.stream().map(e -> dispatchAndWaitForCommit(e)).collect(Collectors.toList());

		var failedTxns = processedTxns.stream().map(t -> transactionStatusService.getTransactionStatus(t.getTxn().getId())).filter(s -> !CONFIRMED.equals(s)).collect(Collectors.toList());

		assertTrue(failedTxns.isEmpty());

		clientApiStore.queueFlushProcessor().process(ScheduledQueueFlush.create());

		// no change
		//Thread.sleep(500);

		var remainingInInitial = berkeleyClientApiStore.getTokenBalances(initialAddr, ClientApiStore.BalanceType.SPENDABLE)
			.onSuccess(list -> {
							System.out.println(" xx " + list.get(0).getAmount());
						})
			.onFailureDo(() -> fail("Result from berkley failed"));
	}
}