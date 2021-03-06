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

package com.radixdlt.application.misc;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.FaucetTokensTransfer;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.stream.Collectors;

public class FaucetTokensTransferConstructor implements ActionConstructor<FaucetTokensTransfer> {
	private static final Amount AMOUNT_TO_TRANSFER = Amount.ofTokens(10);

	@Override
	public void construct(FaucetTokensTransfer action, TxBuilder txBuilder) throws TxBuilderException {
		var map = new HashMap<REAddr, UInt256>();
		var buf = ByteBuffer.allocate(2 + 1 + ECPublicKey.COMPRESSED_BYTES);
		buf.put(SubstateTypeId.TOKENS.id());
		buf.put((byte) 0);
		buf.put(action.from().getBytes());

		var index = SubstateIndex.create(buf.array(), TokensInAccount.class);
		try (var cursor = txBuilder.readIndex(index, false)) {
			cursor
				.filter(p -> p.getHoldingAddr().equals(action.from()))
				.forEachRemaining(t -> map.merge(t.getResourceAddr(), t.getAmount(), UInt256::add));
		}

		var toSend = map.entrySet().stream()
			.filter(e -> e.getValue().compareTo(AMOUNT_TO_TRANSFER.toSubunits()) >= 0)
			.collect(Collectors.toList());
		for (var e : toSend) {
			var change = txBuilder.downFungible(
				index,
				p -> p.getResourceAddr().equals(e.getKey()) && p.getHoldingAddr().equals(action.from()),
				AMOUNT_TO_TRANSFER.toSubunits(),
				() -> new TxBuilderException("Not enough balance for transfer.")
			);
			if (!change.isZero()) {
				txBuilder.up(new TokensInAccount(action.from(), e.getKey(), change));
			}
			txBuilder.up(new TokensInAccount(action.to(), e.getKey(), AMOUNT_TO_TRANSFER.toSubunits()));
			txBuilder.end();
		}
	}
}
