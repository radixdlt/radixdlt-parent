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
package com.radixdlt.api.service;

import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.ClientApiStore.BalanceType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.data.BalanceEntry.createBalance;

public class ArchiveAccountServiceTest {
	private static final ECPublicKey OWNER_KEY = ECKeyPair.generateNew().getPublicKey();
	private static final REAddr OWNER_ACCOUNT = REAddr.ofPubKeyAccount(OWNER_KEY);
	private static final ECPublicKey TOKEN_KEY = ECKeyPair.generateNew().getPublicKey();

	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private final ClientApiStore clientApiStore = mock(ClientApiStore.class);
	private final ArchiveAccountService archiveService = new ArchiveAccountService(clientApiStore);

	@Test
	public void testGetTokenBalancesForFunds() {
		var address = TOKEN_KEY;
		var address1 = REAddr.ofHashedKey(address, "fff");
		var rri1 = addressing.forResources().of("fff", address1);
		var address2 = REAddr.ofHashedKey(address, "rar");
		var rri2 = addressing.forResources().of("rar", address2);
		var balance1 = createBalance(OWNER_ACCOUNT, null, rri1, UInt384.FIVE);
		var balance2 = createBalance(OWNER_ACCOUNT, null, rri2, UInt384.NINE);
		var balances = Result.ok(List.of(balance1, balance2));

		when(clientApiStore.getTokenBalances(OWNER_ACCOUNT, BalanceType.SPENDABLE))
			.thenReturn(balances);

		archiveService.getTokenBalances(OWNER_ACCOUNT)
			.onSuccess(list -> {
				assertEquals(2, list.size());
				assertEquals(UInt384.FIVE, list.get(0).getAmount());
				assertEquals(UInt384.NINE, list.get(1).getAmount());
			})
			.onFailureDo(Assert::fail);
	}

	@Test
	@Ignore
	public void testGetTokenBalancesForStakes() {
		var address = TOKEN_KEY;
		var address1 = REAddr.ofHashedKey(address, "fff");
		var rri1 = addressing.forResources().of("fff", address1);
		var address2 = REAddr.ofHashedKey(address, "rar");
		var rri2 = addressing.forResources().of("rar", address2);
		var balance1 = createBalance(OWNER_ACCOUNT, null, rri1, UInt384.FIVE);
		var balance2 = createBalance(OWNER_ACCOUNT, null, rri2, UInt384.NINE);
		var balance3 = createBalance(OWNER_ACCOUNT, null,
									 addressing.forResources().of("xrd", REAddr.ofNativeToken()), UInt384.TWO
		);
		var balances = Result.ok(List.of(balance1, balance2, balance3));

		when(clientApiStore.getTokenBalances(OWNER_ACCOUNT, BalanceType.STAKES))
			.thenReturn(balances);

		archiveService.getStakePositions(OWNER_ACCOUNT)
			.onSuccess(list -> {
				assertEquals(3, list.size());
				assertEquals(UInt384.FIVE, list.get(0).getAmount());
				assertEquals(UInt384.NINE, list.get(1).getAmount());
				assertEquals(UInt384.TWO, list.get(2).getAmount());
			})
			.onFailureDo(Assert::fail);
	}

	@Test
	public void testGetTransactionHistory() {
		var entry = createTxHistoryEntry();

		when(clientApiStore.getTransactionHistory(eq(OWNER_ACCOUNT), eq(1), eq(Optional.empty())))
			.thenReturn(Result.ok(List.of(entry)));

		archiveService.getTransactionHistory(OWNER_ACCOUNT, 1, Optional.empty())
			.onSuccess(tuple -> tuple.map((cursor, list) -> {
				assertTrue(cursor.isPresent());
				assertEquals(entry.timestamp(), cursor.get());

				assertEquals(1, list.size());
				assertEquals(entry, list.get(0));

				return null;
			}))
			.onFailureDo(Assert::fail);
	}

	private TxHistoryEntry createTxHistoryEntry() {
		var now = Instant.ofEpochMilli(Instant.now().toEpochMilli());
		var action = ActionEntry.unknown();
		return TxHistoryEntry.create(AID.ZERO, now, UInt256.ONE, "text", List.of(action));
	}
}