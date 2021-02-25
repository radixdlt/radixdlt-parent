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

package com.radixdlt.client.core.ledger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;

import java.util.Optional;

/**
 * Wrapper reducer class for the Atom Store
 */
public final class InMemoryAtomStoreReducer {
	private final InMemoryAtomStore atomStore;

	private InMemoryAtomStoreReducer(InMemoryAtomStore atomStore) {
		this.atomStore = atomStore;
	}

	public static InMemoryAtomStoreReducer create(final InMemoryAtomStore atomStore) {
		return new InMemoryAtomStoreReducer(atomStore);
	}


	public void reduce(RadixNodeAction inputAction) {
		if (inputAction instanceof FetchAtomsObservationAction) {
			var action = (FetchAtomsObservationAction) inputAction;

			this.atomStore.store(action.getAddress(), action.getObservation());
		} else if (inputAction instanceof SubmitAtomStatusAction) {

			// Soft storage of atoms so that atoms which are submitted and stored can
			// be immediately used instead of having to wait for fetch atom events.
			final var action = (SubmitAtomStatusAction) inputAction;
			final var atom = action.getAtom();

			if (action.getStatusNotification().getAtomStatus() == AtomStatus.STORED) {
				final long timestamp = retrieveTimestamp(action.getStatusNotification().getData());
				atom.addresses().forEach(address -> {
					this.atomStore.store(address, AtomObservation.softStored(atom, timestamp));
					this.atomStore.store(address, AtomObservation.head());
				});
			}
		}
	}

	private static long retrieveTimestamp(JsonObject data) {
		return Optional.ofNullable(data)
			.map(d -> d.get("timestamp"))
			.map(JsonElement::getAsLong)
			.orElse(Long.MIN_VALUE);
	}
}
