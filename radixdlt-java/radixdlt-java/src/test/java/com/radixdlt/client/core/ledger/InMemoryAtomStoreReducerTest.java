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

import org.junit.Test;
import org.mockito.Mockito;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatusEvent;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import com.radixdlt.identifiers.RadixAddress;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.core.atoms.AtomStatus.STORED;

public class InMemoryAtomStoreReducerTest {
	@Test
	public void when_fetch_atoms_head_action_received__head_should_be_received_by_store() {
		var atomStore = mock(InMemoryAtomStore.class);
		var reducer = InMemoryAtomStoreReducer.create(atomStore);
		var address = mock(RadixAddress.class);
		var node = mock(RadixNode.class);

		reducer.reduce(FetchAtomsObservationAction.create("hi", address, node, AtomObservation.head()));

		verify(atomStore, times(1)).store(eq(address), argThat(AtomObservation::isHead));
	}

	@Test
	public void when_stored_action_received__a_soft_store_observation_should_be_stored() {
		var atomStore = mock(InMemoryAtomStore.class);
		var reducer = InMemoryAtomStoreReducer.create(atomStore);
		var address = mock(RadixAddress.class);
		var atom = mock(Atom.class);

		when(atom.addresses()).thenAnswer(inv -> Stream.of(address));

		var node = mock(RadixNode.class);
		var action = SubmitAtomStatusAction.create("different-id", atom, node, AtomStatusEvent.create(STORED, null));
		reducer.reduce(action);

		var inOrder = Mockito.inOrder(atomStore, atomStore);
		inOrder.verify(atomStore)
			.store(eq(address), argThat(o -> o.isStore() && o.getAtom().equals(atom) && o.getUpdateType().isSoft()));
		inOrder.verify(atomStore).store(eq(address), argThat(AtomObservation::isHead));
	}
}