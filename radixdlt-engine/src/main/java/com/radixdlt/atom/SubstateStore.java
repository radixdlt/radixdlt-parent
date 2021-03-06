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

package com.radixdlt.atom;

import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;

import java.util.Optional;

/**
 * Store which contains an index into up substates
 */
public interface SubstateStore {

	CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index);
	Optional<RawSubstateBytes> get(SystemMapKey key);

	static SubstateStore empty() {
		return new SubstateStore() {
			@Override
			public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
				return CloseableCursor.empty();
			}

			@Override
			public Optional<RawSubstateBytes> get(SystemMapKey key) {
				return Optional.empty();
			}
		};
	}
}
