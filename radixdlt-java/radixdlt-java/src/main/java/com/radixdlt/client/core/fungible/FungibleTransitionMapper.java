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

package com.radixdlt.client.core.fungible;

import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Helper class for transitioning fungible particles
 *
 * @param <T> input particle class
 * @param <U> output particle class
 */
public final class FungibleTransitionMapper<T extends Particle, U extends Particle> {
	private final Function<T, UInt256> inputAmountMapper;
	private final Function<UInt256, T> inputCreator;
	private final Function<UInt256, U> outputCreator;

	public FungibleTransitionMapper(
		Function<T, UInt256> inputAmountMapper,
		Function<UInt256, T> inputCreator,
		Function<UInt256, U> outputCreator
	) {
		this.inputAmountMapper = Objects.requireNonNull(inputAmountMapper);
		this.inputCreator = Objects.requireNonNull(inputCreator);
		this.outputCreator = Objects.requireNonNull(outputCreator);
	}

	public List<SpunParticle> mapToParticles(
		List<T> currentParticles,
		UInt256 totalAmountToTransfer
	) throws NotEnoughFungiblesException {
		final var spunParticles = new ArrayList<SpunParticle>();

		spunParticles.add(SpunParticle.up(outputCreator.apply(totalAmountToTransfer)));

		var amountLeftToTransfer = totalAmountToTransfer;

		for (var p : currentParticles) {
			spunParticles.add(SpunParticle.down(p));

			var particleAmount = inputAmountMapper.apply(p);

			if (particleAmount.compareTo(amountLeftToTransfer) > 0) {
				var sendBackToSelf = particleAmount.subtract(amountLeftToTransfer);
				spunParticles.add(SpunParticle.up(inputCreator.apply(sendBackToSelf)));

				return spunParticles;
			} else if (particleAmount.compareTo(amountLeftToTransfer) == 0) {
				return spunParticles;
			}

			amountLeftToTransfer = amountLeftToTransfer.subtract(particleAmount);
		}

		throw new NotEnoughFungiblesException(totalAmountToTransfer, totalAmountToTransfer.subtract(amountLeftToTransfer));
	}
}
