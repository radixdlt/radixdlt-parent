/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesErrorAction;
import com.radixdlt.client.core.network.actions.GetLivePeersRequestAction;
import com.radixdlt.client.core.network.actions.GetLivePeersResultAction;
import com.radixdlt.client.core.network.actions.GetNodeDataRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResponseAction;
import com.radixdlt.client.core.network.actions.NodeUniverseMismatch;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.reactivex.Observable;

/**
 * Epic which manages simple bootstrapping and discovers nodes one degree out from the initial seeds.
 */
public final class DiscoverNodesEpic implements RadixNetworkEpic {
	private final Observable<RadixNode> seeds;
	private final RadixUniverseConfig config;

	private DiscoverNodesEpic(Observable<RadixNode> seeds, RadixUniverseConfig config) {
		this.seeds = seeds;
		this.config = config;
	}

	public static DiscoverNodesEpic create(Observable<RadixNode> seeds, RadixUniverseConfig config) {
		Objects.requireNonNull(seeds);
		Objects.requireNonNull(config);

		return new DiscoverNodesEpic(seeds, config);
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		var getSeedUniverses = updates
			.ofType(DiscoverMoreNodesAction.class)
			.firstOrError()
			.flatMapObservable(i -> seeds)
			.<RadixNodeAction>map(GetUniverseRequestAction::create)
			.onErrorReturn(DiscoverMoreNodesErrorAction::create);

		// TODO: Store universes in node table instead and filter out node in FindANodeEpic
		var seedUniverseMismatch = updates
			.ofType(GetUniverseResponseAction.class)
			.filter(u -> !u.getResult().equals(config))
			.map(u -> NodeUniverseMismatch.create(u.getNode(), config, u.getResult()));

		var connectedSeeds = updates
			.ofType(GetUniverseResponseAction.class)
			.filter(u -> u.getResult().equals(config))
			.map(GetUniverseResponseAction::getNode)
			.publish()
			.autoConnect(3);

		var addSeeds = connectedSeeds.map(AddNodeAction::create);
		var addSeedData = connectedSeeds.map(GetNodeDataRequestAction::create);
		var addSeedSiblings = connectedSeeds.map(GetLivePeersRequestAction::create);

		var addNodes = updates
			.ofType(GetLivePeersResultAction.class)
			.flatMap(u -> combineLatest(networkState, u));

		return Observable.merge(List.of(
			addSeeds,
			addSeedData,
			addSeedSiblings,
			addNodes,
			getSeedUniverses,
			seedUniverseMismatch
		));
	}

	private Observable<AddNodeAction> combineLatest(final Observable<RadixNetworkState> networkState, final GetLivePeersResultAction u) {
		return Observable.combineLatest(
			Observable.just(u.getResult()),
			Observable.concat(networkState.firstOrError().toObservable(), Observable.never()),
			(data, state) ->
				data.stream()
					.map(d -> toAddNodeAction(u, state, d))
					.filter(Objects::nonNull)
					.collect(Collectors.toSet())
		).flatMapIterable(i -> i);
	}

	private AddNodeAction toAddNodeAction(GetLivePeersResultAction action, RadixNetworkState state, NodeRunnerData data) {
		var node = new RadixNode(data.getIp(), action.getNode().isSsl(), action.getNode().getPort());
		return state.getNodeStates().containsKey(node) ? null : AddNodeAction.create(node, data);
	}
}
