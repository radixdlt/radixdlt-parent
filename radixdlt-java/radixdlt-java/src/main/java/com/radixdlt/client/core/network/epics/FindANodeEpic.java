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

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.CloseWebSocketAction;
import com.radixdlt.client.core.network.actions.ConnectWebSocketAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.FindANodeRequestAction;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.reactivex.Observable;

/**
 * Epic which finds a connected sharded node when a FindANode request is received. If there are none found,
 * then the epic attempts to start connections.
 */
public final class FindANodeEpic implements RadixNetworkEpic {
	private static final int MAX_SIMULTANEOUS_CONNECTION_REQUESTS = 2;
	private static final int NEXT_CONNECTION_THROTTLE_TIMEOUT_SECS = 1;

	private final RadixPeerSelector selector;

	private FindANodeEpic(RadixPeerSelector selector) {
		this.selector = selector;
	}

	public static FindANodeEpic create(final RadixPeerSelector selector) {
		return new FindANodeEpic(selector);
	}

	private List<RadixNodeAction> nextConnectionRequest(RadixNetworkState state) {
		final Map<WebSocketStatus, List<RadixNode>> statusMap = buildStatusMap(state);

		final long connectingNodeCount = statusMap.get(WebSocketStatus.CONNECTING).size();

		if (connectingNodeCount < MAX_SIMULTANEOUS_CONNECTION_REQUESTS) {
			var disconnectedPeers = statusMap.get(WebSocketStatus.DISCONNECTED);

			if (disconnectedPeers.isEmpty()) {
				return List.of(DiscoverMoreNodesAction.create());
			} else {
				return List.of(ConnectWebSocketAction.create(selector.apply(disconnectedPeers)));
			}
		}

		return List.of();
	}

	private Map<WebSocketStatus, List<RadixNode>> buildStatusMap(final RadixNetworkState state) {
		return Arrays.stream(WebSocketStatus.values())
			.collect(Collectors.toMap(
				Function.identity(),
				s -> state.getNodeStates().entrySet().stream()
					.filter(e -> e.getValue().getStatus().equals(s))
					.map(Entry::getKey)
					.collect(Collectors.toList())
			));
	}

	private static List<RadixNode> getConnectedNodes(RadixNetworkState state) {
		return state.getNodeStates().entrySet().stream()
			.filter(entry -> entry.getValue().getStatus().equals(WebSocketStatus.CONNECTED))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> stateObservable) {
		return actions.ofType(FindANodeRequestAction.class)
			.flatMap(a -> {
				var connectedNodes = stateObservable
					.map(FindANodeEpic::getConnectedNodes)
					.replay(1)
					.autoConnect(2);

				// Stream to find node
				var selectedNode = connectedNodes
					.filter(viablePeerList -> !viablePeerList.isEmpty())
					.firstOrError()
					.map(selector::apply)
					.<RadixNodeAction>map(n -> FindANodeResultAction.create(n, a))
					.cache()
					.toObservable();

				// Stream of new actions to find a new node
				var findConnectionActionsStream = connectedNodes
					.filter(List::isEmpty)
					.firstOrError()
					.ignoreElement()
					.andThen(
						Observable
							.interval(0, NEXT_CONNECTION_THROTTLE_TIMEOUT_SECS, TimeUnit.SECONDS)
							.withLatestFrom(stateObservable, (i, s) -> s)
							.flatMapIterable(this::nextConnectionRequest)
					)
					.takeUntil(selectedNode)
					.replay(1)
					.autoConnect(2);

				// Cleanup and close connections which never worked out
				var cleanupConnections = findConnectionActionsStream
					.ofType(ConnectWebSocketAction.class)
					.flatMap(c -> {
						final var node = c.getNode();
						return selectedNode
							.map(RadixNodeAction::getNode)
							.filter(selected -> !node.equals(selected))
							.map(i -> CloseWebSocketAction.create(node));
					});

				return findConnectionActionsStream.concatWith(selectedNode).mergeWith(cleanupConnections);
			});
	}
}
