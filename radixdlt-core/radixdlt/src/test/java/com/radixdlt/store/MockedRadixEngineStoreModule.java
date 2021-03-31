/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.store;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.atom.Atom;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;

public class MockedRadixEngineStoreModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
	}

	private ParsedTransaction toParsed(Atom atom, InMemoryEngineStore<LedgerAndBFTProof> store) {
		var instructions = new ArrayList<ParsedInstruction>();
		for (int i = 0; i < atom.getInstructions().size(); i++) {
			var instruction = atom.getInstructions().get(i);

			if (!instruction.isPush()) {
				continue;
			}

			Spin nextSpin = instruction.getNextSpin();

			final Particle particle;
			final SubstateId substateId;
			try {
				if (instruction.getMicroOp() == REInstruction.REOp.UP) {
					particle = SubstateSerializer.deserialize(instruction.getData());
					substateId = SubstateId.ofSubstate(atom, i);
				} else if (instruction.getMicroOp() == REInstruction.REOp.VDOWN) {
					particle = SubstateSerializer.deserialize(instruction.getData());
					substateId = SubstateId.ofVirtualSubstate(instruction.getData());
				} else if (instruction.getMicroOp() == REInstruction.REOp.DOWN) {
					substateId = SubstateId.fromBytes(instruction.getData());
					var storedParticle = store.loadUpParticle(null, substateId);
					particle = storedParticle.orElseThrow();
				} else if (instruction.getMicroOp() == REInstruction.REOp.LDOWN) {
					int index = Ints.fromByteArray(instruction.getData());
					var dson = atom.getInstructions().get(index).getData();
					particle = SubstateSerializer.deserialize(dson);
					substateId = SubstateId.ofSubstate(atom, index);
				} else {
					throw new IllegalStateException();
				}
			} catch (DeserializeException e) {
				throw new IllegalStateException();
			}

			var parsed = ParsedInstruction.of(instruction, Substate.create(particle, substateId), nextSpin);
			instructions.add(parsed);
		}

		return new ParsedTransaction(atom, instructions);
	}

	@Provides
	@Singleton
	private EngineStore<LedgerAndBFTProof> engineStore(
		@Genesis List<Atom> genesisAtoms,
		Hasher hasher,
		Serialization serialization,
		@Genesis ImmutableList<ECKeyPair> genesisValidatorKeys
	) {
		var inMemoryEngineStore = new InMemoryEngineStore<LedgerAndBFTProof>();
		for (var genesisAtom : genesisAtoms) {
			byte[] payload = serialization.toDson(genesisAtom, DsonOutput.Output.ALL);
			Command command = new Command(payload);
			BFTValidatorSet validatorSet = BFTValidatorSet.from(genesisValidatorKeys.stream()
					.map(k -> BFTValidator.from(BFTNode.create(k.getPublicKey()), UInt256.ONE)));
			if (!inMemoryEngineStore.containsAtom(genesisAtom)) {
				var txn = inMemoryEngineStore.createTransaction();
				var instruction = toParsed(genesisAtom, inMemoryEngineStore);
				inMemoryEngineStore.storeAtom(txn, instruction);
				txn.commit();
			}
		}
		return inMemoryEngineStore;
	}
}
