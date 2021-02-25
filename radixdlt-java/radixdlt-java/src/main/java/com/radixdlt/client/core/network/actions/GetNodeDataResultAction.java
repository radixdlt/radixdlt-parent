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

package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;

import java.util.Objects;

/**
 * A dispatchable action response for a given node data request
 */
public final class GetNodeDataResultAction implements JsonRpcResultAction<NodeRunnerData> {
	private final RadixNode node;
	private final NodeRunnerData data;

	private GetNodeDataResultAction(RadixNode node, NodeRunnerData data) {
		this.node = node;
		this.data = data;
	}

	public static GetNodeDataResultAction create(RadixNode node, NodeRunnerData data) {
		Objects.requireNonNull(node);
		Objects.requireNonNull(data);

		return new GetNodeDataResultAction(node, data);
	}

	@Override
	public NodeRunnerData getResult() {
		return data;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "GET_NODE_DATA_RESPONSE " + node + " " + data;
	}
}
