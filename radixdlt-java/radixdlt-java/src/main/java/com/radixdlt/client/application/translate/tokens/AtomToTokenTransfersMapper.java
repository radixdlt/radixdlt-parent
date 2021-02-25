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

import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.AtomToExecutedActionsMapper;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.Observable;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

/**
 * Maps an atom to some number of token transfer actions.
 */
public class AtomToTokenTransfersMapper implements AtomToExecutedActionsMapper<TokenTransfer> {
	private static final Collector<SpunParticle, ?, Map<RRI, Map<RadixAddress, BigDecimal>>> GROUPING_COLLECTOR =
		groupingBy(
			sp -> sp.getParticle(TransferrableTokensParticle.class).getTokenDefinitionReference(),
			groupingBy(
				sp -> sp.getParticle(TransferrableTokensParticle.class).getAddress(),
				reducing(BigDecimal.ZERO, AtomToTokenTransfersMapper::consumableToAmount, BigDecimal::add)
			)
		);

	public AtomToTokenTransfersMapper() {
		// Nothing to do here
	}

	@Override
	public Class<TokenTransfer> actionClass() {
		return TokenTransfer.class;
	}


	@Override
	public Observable<TokenTransfer> map(Atom atom, RadixIdentity identity) {
		return Observable.fromIterable(atom.particleGroups()
										   .flatMap(this::mapTransfers)
										   .collect(Collectors.toList()));
	}

	private Stream<? extends TokenTransfer> mapTransfers(ParticleGroup pg) {
		var tokenSummary = pg.spunParticles()
			.filter(sp -> sp.getParticle() instanceof TransferrableTokensParticle)
			.collect(GROUPING_COLLECTOR);

		return tokenSummary.entrySet().stream().map(e -> toTokenTransfer(pg, e));
	}

	private TokenTransfer toTokenTransfer(final ParticleGroup pg, final Map.Entry<RRI, Map<RadixAddress, BigDecimal>> entry) {
		var summary = validateSummary(entry.getValue().entrySet());
		var fromToPair = determineSourceAndDestination(summary);

		return new TokenTransfer(
			fromToPair.getFirst(),
			fromToPair.getSecond(),
			entry.getKey(),
			summary.get(0).getValue().abs(),
			Optional.ofNullable(pg.getMetaData().get("attachment"))
				.map(Bytes::fromBase64String).orElse(null)
		);
	}

	private Pair<RadixAddress, RadixAddress> determineSourceAndDestination(final List<Map.Entry<RadixAddress, BigDecimal>> summary) {
		if (summary.size() == 1) {
			return Pair.of(
				summary.get(0).getValue().signum() <= 0 ? summary.get(0).getKey() : null,
				summary.get(0).getValue().signum() < 0 ? null : summary.get(0).getKey()
			);
		}

		if (summary.get(0).getValue().signum() > 0) {
			return Pair.of(summary.get(1).getKey(), summary.get(0).getKey());
		} else {
			return Pair.of(summary.get(0).getKey(), summary.get(1).getKey());
		}
	}

	private List<Map.Entry<RadixAddress, BigDecimal>> validateSummary(final Collection<Map.Entry<RadixAddress, BigDecimal>> summary) {
		if (summary.size() > 2) {
			throw new IllegalStateException("More than two participants in token transfer. Unable to handle: " + summary);
		}
		return List.copyOf(summary);
	}

	private static BigDecimal consumableToAmount(SpunParticle sp) {
		var amount = TokenUnitConversions.subunitsToUnits(sp.getParticle(TransferrableTokensParticle.class).getAmount());
		return sp.getSpin() == Spin.DOWN ? amount.negate() : amount;
	}
}
