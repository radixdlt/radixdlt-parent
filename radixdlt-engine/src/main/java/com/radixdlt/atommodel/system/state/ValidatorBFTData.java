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

package com.radixdlt.atommodel.system.state;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;

public final class ValidatorBFTData implements Particle {
	private final ECPublicKey validatorKey;
	private final long proposalsCompleted;

	public ValidatorBFTData(
		ECPublicKey validatorKey,
		long proposalsCompleted
	) {
		this.validatorKey = validatorKey;
		this.proposalsCompleted = proposalsCompleted;
	}

	public ECPublicKey validatorKey() {
		return validatorKey;
	}

	public long proposalsCompleted() {
		return proposalsCompleted;
	}

	@Override
	public int hashCode() {
		return Objects.hash(validatorKey, proposalsCompleted);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ValidatorBFTData)) {
			return false;
		}

		var other = (ValidatorBFTData) o;
		return Objects.equals(this.validatorKey, other.validatorKey)
			&& this.proposalsCompleted == other.proposalsCompleted;
	}

	@Override
	public String toString() {
		return String.format(
			"%s{validatorKey=%s proposalsCompleted=%s}", this.getClass().getSimpleName(), validatorKey, proposalsCompleted
		);
	}
}