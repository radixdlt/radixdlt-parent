/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.data.action;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.stream.Stream;

class TransferAction implements TransactionAction {
	private final REAddr from;
	private final REAddr to;
	private final UInt256 amount;
	private final REAddr rri;

	TransferAction(REAddr from, REAddr to, UInt256 amount, REAddr rri) {
		this.from = from;
		this.to = to;
		this.amount = amount;
		this.rri = rri;
	}

	@Override
	public Stream<TxAction> toAction() {
		return Stream.of(new TransferToken(TransactionAction.rriValue(rri), from, to, amount));
	}
}
