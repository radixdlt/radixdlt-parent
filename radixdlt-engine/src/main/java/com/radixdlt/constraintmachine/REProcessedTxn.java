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

package com.radixdlt.constraintmachine;

import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.engine.parser.ParsedTxn;
import com.radixdlt.crypto.ECPublicKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Transaction which has been successfully parsed and state checked by radix engine
 */
public final class REProcessedTxn {
	private final List<List<REStateUpdate>> stateUpdates;
	private final ParsedTxn parsedTxn;

	public REProcessedTxn(
		ParsedTxn parsedTxn,
		List<List<REStateUpdate>> stateUpdates
	) {
		this.parsedTxn = parsedTxn;
		this.stateUpdates = stateUpdates;
	}

	public Optional<byte[]> getMsg() {
		return parsedTxn.getMsg();
	}

	public Optional<ECPublicKey> getSignedBy() {
		return parsedTxn.getSignedBy();
	}

	public Txn getTxn() {
		return parsedTxn.txn();
	}

	// FIXME: Currently a hack, better would be to put this at transaction layer for fees
	public boolean isSystemOnly() {
		return stateUpdates().anyMatch(i -> i.getSubstate().getParticle() instanceof SystemParticle)
			|| stateUpdates().anyMatch(i -> i.getSubstate().getParticle() instanceof RoundData)
			|| stateUpdates().anyMatch(i -> i.getSubstate().getParticle() instanceof EpochData);
	}

	public List<List<REStateUpdate>> getGroupedStateUpdates() {
		return stateUpdates;
	}

	public Stream<REStateUpdate> stateUpdates() {
		return stateUpdates.stream().flatMap(List::stream);
	}

	@Override
	public int hashCode() {
		return Objects.hash(stateUpdates, parsedTxn);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof REProcessedTxn)) {
			return false;
		}

		var other = (REProcessedTxn) o;
		return Objects.equals(this.stateUpdates, other.stateUpdates)
			&& Objects.equals(this.parsedTxn, other.parsedTxn);
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), parsedTxn);
	}
}
