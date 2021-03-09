/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.store;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.ClientAtom;

import java.util.stream.Stream;

public final class LedgerEntryConflict {
	private final ClientAtom ledgerEntry;
	private final ImmutableMap<StoreIndex, ClientAtom> conflictingLedgerEntries;

	public LedgerEntryConflict(ClientAtom ledgerEntry, ImmutableMap<StoreIndex, ClientAtom> conflictingLedgerEntries) {
		this.ledgerEntry = ledgerEntry;
		this.conflictingLedgerEntries = conflictingLedgerEntries;
	}

	public ClientAtom getLedgerEntry() {
		return ledgerEntry;
	}

	public ImmutableMap<StoreIndex, ClientAtom> getConflictingLedgerEntries() {
		return conflictingLedgerEntries;
	}

	public ImmutableSet<AID> getConflictingAids() {
		return conflictingLedgerEntries.values().stream()
			.map(ClientAtom::getAID)
			.collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableSet<AID> getAllAids() {
		return Stream.concat(Stream.of(ledgerEntry), conflictingLedgerEntries.values().stream())
			.map(ClientAtom::getAID)
			.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public String toString() {
		return "LedgerEntryConflict{" + "ledgerEntry=" + ledgerEntry + ", conflictingLedgerEntries=" + conflictingLedgerEntries + '}';
	}
}
