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

package com.radixdlt.identifiers;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Radix resource identifier is a human readable unique identifier into the Ledger which points to a resource.
 */
public final class RRI {
	private static final String NAME_REGEX = "[-0-9A-Za-z]+";
	private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

	// TODO: Will replace this with shardable at some point
	private final RadixAddress address;
	private final String name;

	private RRI(RadixAddress address, String name) {
		this.address = address;
		this.name = name;
	}

	public RadixAddress getAddress() {
		return address;
	}

	public String getName() {
		return name;
	}

	public static RRI of(RadixAddress address, String name) {
		Objects.requireNonNull(address);
		Objects.requireNonNull(name);

		return new RRI(address, name);
	}

	public static RRI from(String s) {
		var split = s.split("/", 3);

		validateRRIFormat(s, split);

		var address = RadixAddress.from(split[1]);
		var name = split[2];

		validateRRIName(s, name);

		return of(address, name);
	}

	private static void validateRRIName(final String s, final String name) {
		if (!NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("RRI name invalid, must match regex '" + NAME_REGEX + "': " + s);
		}
	}

	private static void validateRRIFormat(final String s, final String[] split) {
		if (split.length == 3 && split[0].length() == 0) {
			return;
		}

		throw new IllegalArgumentException("RRI must be of the format /:address/:name (" + s + ")");
	}

	@Override
	public String toString() {
		return "/" + address.toString() + "/" + name;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RRI)) {
			return false;
		}

		RRI rri = (RRI) o;
		return Objects.equals(rri.address, address) && Objects.equals(rri.name, name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, name);
	}
}