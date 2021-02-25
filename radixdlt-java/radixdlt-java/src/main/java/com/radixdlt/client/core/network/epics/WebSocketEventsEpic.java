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
import com.radixdlt.client.core.network.WebSockets;
import com.radixdlt.client.core.network.actions.WebSocketEvent;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;

/**
 * Epic which emits websocket events from each node
 */
public final class WebSocketEventsEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	private WebSocketEventsEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	public static WebSocketEventsEpic create(WebSockets webSockets) {
		return new WebSocketEventsEpic(webSockets);
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return webSockets.getNewNodes().flatMap(this::toWebSocketEvent);
	}

	private ObservableSource<? extends RadixNodeAction> toWebSocketEvent(RadixNode n) {
		return webSockets.getOrCreate(n).getState().map(s -> WebSocketEvent.create(n, s));
	}
}
