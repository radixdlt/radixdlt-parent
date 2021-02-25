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

package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.StatelessActionToParticleGroupsMapper;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.util.List;

import static com.radixdlt.client.application.translate.tokens.TokenUnitConversions.unitsToSubunits;

/**
 * Maps the CreateToken action into it's corresponding particles
 */
public class CreateTokenToParticleGroupsMapper implements StatelessActionToParticleGroupsMapper<CreateTokenAction> {
	@Override
	public List<ParticleGroup> mapToParticleGroups(CreateTokenAction tokenCreation) {
		switch (tokenCreation.getTokenSupplyType()) {
			case FIXED:
				return createFixedSupplyToken(tokenCreation);
			case MUTABLE:
				return createVariableSupplyToken(tokenCreation);
			default:
				throw new IllegalStateException("Unknown supply type: " + tokenCreation.getTokenSupplyType());
		}
	}

	public List<ParticleGroup> createVariableSupplyToken(CreateTokenAction tokenCreation) {
		var token = tokenCreation.toMutableSupplyTokenDefinitionParticle();

		var unallocated = new UnallocatedTokensParticle(
			UInt256.MAX_VALUE,
			unitsToSubunits(tokenCreation.getGranularity()),
			System.currentTimeMillis(),
			token.getRRI(),
			token.getTokenPermissions()
		);

		var tokenCreationGroup = ParticleGroup.of(
			SpunParticle.down(new RRIParticle(token.getRRI())),
			SpunParticle.up(token),
			SpunParticle.up(unallocated)
		);

		if (tokenCreation.getInitialSupply().compareTo(BigDecimal.ZERO) == 0) {
			// No initial supply -> just the token particle
			return List.of(tokenCreationGroup);
		}

		var minted = new TransferrableTokensParticle(
			unitsToSubunits(tokenCreation.getInitialSupply()),
			unitsToSubunits(tokenCreation.getGranularity()),
			tokenCreation.getRRI().getAddress(),
			System.nanoTime(),
			token.getRRI(),
			token.getTokenPermissions()
		);

		var mintGroupBuilder = ParticleGroup.builder()
			.addParticle(unallocated, Spin.DOWN)
			.addParticle(minted, Spin.UP);

		final var leftOver = UInt256.MAX_VALUE.subtract(unitsToSubunits(tokenCreation.getInitialSupply()));

		if (!leftOver.isZero()) {
			var unallocatedLeftOver = new UnallocatedTokensParticle(
				leftOver,
				unitsToSubunits(tokenCreation.getGranularity()),
				System.currentTimeMillis(),
				token.getRRI(),
				token.getTokenPermissions()
			);

			mintGroupBuilder.addParticle(unallocatedLeftOver, Spin.UP);
		}

		return List.of(tokenCreationGroup, mintGroupBuilder.build());
	}

	public List<ParticleGroup> createFixedSupplyToken(CreateTokenAction tokenCreation) {
		var token = tokenCreation.toFixedSupplyTokenDefinitionParticle();

		var tokens = new TransferrableTokensParticle(
			unitsToSubunits(tokenCreation.getInitialSupply()),
			unitsToSubunits(tokenCreation.getGranularity()),
			token.getRRI().getAddress(),
			System.currentTimeMillis(),
			token.getRRI(),
			ImmutableMap.of()
		);

		var rriParticle = new RRIParticle(token.getRRI());
		var tokenCreationGroup = ParticleGroup.of(
			SpunParticle.down(rriParticle),
			SpunParticle.up(token),
			SpunParticle.up(tokens)
		);

		return List.of(tokenCreationGroup);
	}
}
