package com.radix.regression.doublespend;

import static org.assertj.core.api.Assertions.assertThat;

import com.radix.regression.Util;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.client.core.network.epics.DiscoverSingleNodeEpic;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.radix.common.tuples.Pair;

public final class DoubleSpendTestRunner {
	private final Function<RadixApplicationAPI, DoubleSpendTestConfig> testSupplier;

	public DoubleSpendTestRunner(Function<RadixApplicationAPI, DoubleSpendTestConfig> testSupplier) {
		this.testSupplier = testSupplier;
	}

	public void execute(int numRounds) {
		IntStream.range(0, numRounds)
			.forEach(i -> {
				System.out.println("Round " + (i + 1));
				execute();
			});
	}

	public void execute() {
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST, RadixIdentities.createNew());

		DoubleSpendTestConfig doubleSpendTestConfig = testSupplier.apply(api);

		List<Action> initialActions = doubleSpendTestConfig.initialActions();
		Disposable d = api.pull();
		initialActions.stream()
			.map(api::execute)
			.map(Result::toCompletable)
			.forEach(Completable::blockingAwait);
		d.dispose();
		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
		}

		// Retrieve two nodes in the network
		Single<List<RadixNode>> twoNodes = api.getNetworkState()
			.filter(network -> network.getNodes().entrySet().stream()
				.filter(e -> e.getValue().getData().isPresent() && e.getValue().getUniverseConfig().isPresent())
				.count() >= 2)
			.firstOrError()
			.map(state ->
				state.getNodes().entrySet().stream()
					.filter(e -> e.getValue().getUniverseConfig().isPresent())
					.map(Entry::getKey)
					.collect(Collectors.toList())
			);

		// If two nodes don't exist in the network just use one node
		Single<List<RadixNode>> oneNode = api.getNetworkState()
			.filter(network -> network.getNodes().entrySet().stream()
				.filter(e -> e.getValue().getData().isPresent() && e.getValue().getUniverseConfig().isPresent())
				.count() == 1)
			.debounce(3, TimeUnit.SECONDS)
			.firstOrError()
			.map(state ->
				state.getNodes().entrySet().stream()
					.filter(e -> e.getValue().getUniverseConfig().isPresent())
					.map(Entry::getKey)
					.collect(Collectors.toList())
			);

		Observable<RadixApplicationAPI> singleNodeApis = Observable.merge(twoNodes.toObservable(), oneNode.toObservable())
			.firstOrError()
			.flatMapObservable(l -> l.size() == 1 ? Observable.just(l.get(0), l.get(0)) : Observable.fromIterable(l))
			.map(node ->
				RadixApplicationAPI.create(new BootstrapConfig() {
					@Override
					public RadixUniverseConfig getConfig() {
						return RadixUniverseConfigs.getBetanet();
					}

					@Override
					public List<RadixNetworkEpic> getDiscoveryEpics() {
						return Collections.singletonList(new DiscoverSingleNodeEpic(node, RadixUniverseConfigs.getBetanet()));
					}
				},
				api.getMyIdentity())
			)
			.cache();


		// When the account executes two transfers via two different nodes at the same time
		Single<List<Pair<RadixApplicationAPI, List<Action>>>> conflictingAtoms =
			Observable.zip(
				singleNodeApis,
				Observable.fromIterable(doubleSpendTestConfig.conflictingActions()),
				Pair::of
			)
			.toList();

		TestObserver<SubmitAtomAction> submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		conflictingAtoms
			.flattenAsObservable(l -> l)
			.flatMap(a -> Observable.fromIterable(a.getFirst().executeSequentially(a.getSecond()))
				.flatMap(Result::toObservable)
				.takeUntil(s -> {
					if (s instanceof SubmitAtomResultAction) {
						SubmitAtomResultAction submitAtomResultAction = (SubmitAtomResultAction) s;
						return submitAtomResultAction.getType() != SubmitAtomResultActionType.STORED;
					}
					return false;
				})
			)
			.subscribe(submissionObserver);

		Map<ShardedAppStateId, TestObserver<ApplicationState>> testObservers = doubleSpendTestConfig.postConsensusCondition().getStateRequired().stream()
			.collect(Collectors.toMap(
				Pair::getSecond,
				pair -> {
					final String name = pair.getFirst();
					final ShardedAppStateId id = pair.getSecond();
					final RadixApplicationAPI newApi = RadixApplicationAPI.create(Bootstrap.LOCALHOST, RadixIdentities.createNew());
					final TestObserver<ApplicationState> testObserver = TestObserver.create(Util.loggingObserver(name));
					newApi.getState(id.stateClass(), id.address()).subscribe(testObserver);
					return testObserver;
				}
			));

		// Wait for network to resolve conflict
		TestObserver<RadixNodeAction> lastUpdateObserver = TestObserver.create(Util.loggingObserver("Last Update"));
		singleNodeApis.flatMap(singleNodeApi ->
			singleNodeApi.getNetworkController()
			.getActions()
			.filter(a -> a instanceof FetchAtomsObservationAction || a instanceof SubmitAtomAction)
		)
			.debounce(10, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(lastUpdateObserver);
		lastUpdateObserver.awaitTerminalEvent();
		submissionObserver.awaitTerminalEvent();

		Map<ShardedAppStateId, ApplicationState> state = testObservers.entrySet().stream()
			.collect(Collectors.toMap(
				Entry::getKey,
				e -> {
					List<ApplicationState> values = e.getValue().values();
					return values.get(values.size() - 1);
				}
			));
		testObservers.forEach((k,v) -> v.dispose());

		assertThat(state).is(doubleSpendTestConfig.postConsensusCondition().getCondition());
	}
}
