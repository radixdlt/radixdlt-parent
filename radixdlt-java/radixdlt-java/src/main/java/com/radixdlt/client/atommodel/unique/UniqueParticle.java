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

package com.radixdlt.client.atommodel.unique;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;

import java.util.Set;

@SerializerId2("radix.particles.unique")
public final class UniqueParticle extends Particle implements Identifiable {
	@JsonProperty("name")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String name;

	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress address;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private final long nonce;

	UniqueParticle() {
		// Serializer only
		this.name = null;
		this.address = null;
		this.nonce = 0;
	}

	private UniqueParticle(RadixAddress address, String name) {
		this.address = address;
		this.name = name;
		this.nonce = System.nanoTime();
	}

	public static UniqueParticle create(RRI rri) {
		return create(rri.getAddress(), rri.getName());
	}

	public static UniqueParticle create(RadixAddress address, String name) {
		return new UniqueParticle(address, name);
	}

	@Override
	public Set<EUID> getDestinations() {
		return Set.of(address.euid());
	}

	public String getName() {
		return name;
	}

	@Override
	public RRI getRRI() {
		return RRI.of(address, name);
	}
}
