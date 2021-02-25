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

package com.radixdlt.client.application.translate.unique;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.utils.Pair;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AlreadyUsedUniqueIdReasonMapper implements AtomErrorToExceptionReasonMapper {

	public static final Logger LOGGER = LogManager.getLogger(AlreadyUsedUniqueIdReasonMapper.class);

	@Override
	public Stream<ActionExecutionExceptionReason> mapAtomErrorToExceptionReasons(Atom atom, JsonObject errorData) {
		if (!errorData.has("pointerToIssue")) {
			return Stream.empty();
		}

		var particleIssue = this.extractParticleFromPointerToIssue(atom, errorData.get("pointerToIssue"));

		if (particleIssue.isPresent()) {
			var pair = particleIssue.get();
			var particle = pair.getSecond().getParticle();

			if (particle instanceof RRIParticle) {
				var rriParticle = (RRIParticle) particle;

				return pair.getFirst()
					.particles(Spin.UP)
					.filter(UniqueParticle.class::isInstance)
					.map(UniqueParticle.class::cast)
					.filter(u -> u.getRRI().equals(rriParticle.getRRI()))
					.map(p -> new AlreadyUsedUniqueIdReason(UniqueId.create(p.getRRI().getAddress(), p.getName())));
			}
		}

		return Stream.empty();
	}

	/**
	 * Extract the affected particle from pointerToIssue of an Atom
	 *
	 * @param atom The atom the particle is in
	 * @param pointerToIssueJson The pointer to issue in Json form returned by the node
	 *
	 * @return The SpunParticle if it could be extracted
	 */
	private Optional<Pair<ParticleGroup, SpunParticle>> extractParticleFromPointerToIssue(Atom atom, JsonElement pointerToIssueJson) {
		try {
			var pointerToIssue = pointerToIssueJson.getAsString();
			var groupIndexStr = pointerToIssue.split("/")[2];
			var particleIndexStr = pointerToIssue.substring(pointerToIssue.lastIndexOf('/') + 1);

			int groupIndex = Integer.parseInt(groupIndexStr);
			int particleIndex = Integer.parseInt(particleIndexStr);

			var particleGroup = atom.particleGroups().collect(Collectors.toList()).get(groupIndex);

			return Optional.of(Pair.of(
				particleGroup,
				particleGroup.spunParticles().collect(Collectors.toList()).get(particleIndex)
			));
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			LOGGER.error("Malformed pointerToIssue");

			return Optional.empty();
		}
	}
}
