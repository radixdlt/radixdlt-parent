/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.application;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.FeeProcessor;
import com.radixdlt.client.application.translate.InvalidAddressMagicException;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.network.RadixNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.annotations.Nullable;

import static java.util.Optional.ofNullable;

/**
 * Represents an atomic transaction to be committed to the ledger
 */
public final class Transaction {
	private final RadixApplicationAPI api;
	private final FeeProcessor feeProcessor;
	private final String uuid;
	private final List<Action> workingArea = new ArrayList<>();
	private String message = null;

	Transaction(final RadixApplicationAPI api, final FeeProcessor feeProcessor) {
		this.api = api;
		this.feeProcessor = feeProcessor;
		this.uuid = UUID.randomUUID().toString();
	}

	/**
	 * Sets the atom's message to the specified message.
	 *
	 * @param message The message to use for the atom
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * Add an action to the working area
	 *
	 * @param action action to add to the working area
	 */
	public void addToWorkingArea(Action action) {
		workingArea.add(action);
	}

	/**
	 * Retrieves the shards and particle types required to execute the
	 * actions in the current working area.
	 *
	 * @return set of shard + particle types
	 */
	public Set<ShardedParticleStateId> getWorkingAreaRequirements() {
		var stateMappers = api.getRequiredStateMappers();

		return workingArea.stream()
			.filter(action -> stateMappers.containsKey(action.getClass()))
			.flatMap(action -> stateMappers.get(action.getClass()).apply(action).stream())
			.collect(Collectors.toSet());
	}

	/**
	 * Move all actions in the current working area to staging
	 */
	public void stageWorkingArea() throws StageActionException {
		workingArea.forEach(this::stage);
		workingArea.clear();
	}

	/**
	 * Add an action to staging area in preparation for commitAndPush.
	 * Collects the necessary particles to make the action happen.
	 *
	 * @param action action to add to staging area.
	 */
	public Transaction stage(Action action) throws StageActionException {
		var actionMappers = api.getActionMappers();
		var statefulMapper = actionMappers.get(action.getClass());

		validateMapper(action, actionMappers, statefulMapper);

		var stateMappers = api.getRequiredStateMappers();

		var particles = Optional.ofNullable(stateMappers.get(action.getClass()))
			.map(m -> m.apply(action)).orElseGet(Set::of).stream()
			.flatMap(ctx -> api.getAtomStore().getUpParticles(ctx.address(), uuid).filter(ctx.particleClass()::isInstance));

		var pgs = statefulMapper.apply(action, particles);

		pgs.stream()
			.peek(pg -> api.getAtomStore().stageParticleGroup(uuid, pg))
			.flatMap(pg -> pg.getSpunParticles().stream())
			.flatMap(sp -> sp.getParticle().getShardables().stream())
			.filter(address -> address.getMagicByte() != api.getMagic())
			.findFirst()
			.ifPresent(address -> {
				throw new InvalidAddressMagicException(address, api.getMagic());
			});

		return this;
	}

	private void validateMapper(
		final Action action,
		final Map<Class<? extends Action>, BiFunction<Action, Stream<Particle>, List<ParticleGroup>>> actionMappers,
		final BiFunction<Action, Stream<Particle>, List<ParticleGroup>> statefulMapper
	) {
		if (statefulMapper != null) {
			return;
		}

		var message = String.format("Unknown action class: %s. Available: %s", action.getClass(), actionMappers.keySet());
		throw new IllegalArgumentException(message);
	}

	/**
	 * Add a particle group to staging area in preparation for commitAndPush.
	 *
	 * @param particleGroup Particle group to add to staging area.
	 */
	public Transaction stage(ParticleGroup particleGroup) {
		particleGroup.getSpunParticles().stream()
			.flatMap(sp -> sp.getParticle().getShardables().stream())
			.filter(address -> address.getMagicByte() != api.getMagic())
			.findFirst()
			.ifPresent(address -> {
				throw new InvalidAddressMagicException(address, api.getMagic());
			});

		api.getAtomStore().stageParticleGroup(uuid, particleGroup);

		return this;
	}

	/**
	 * Add multiple particle groups at once.
	 *
	 * @param particleGroups Particle group to add to staging area.
	 */
	public Transaction stage(Collection<ParticleGroup> particleGroups) {
		particleGroups.forEach(this::stage);
		return this;
	}

	/**
	 * Creates an atom composed of all of the currently staged particles.
	 * If the specified fee is non-null, a fee of that amount will be included in
	 * the built atom, otherwise the fee will be computed based on the atom properties.
	 *
	 * @param fee the fee to include in the atom, or {@code null} if the fee should be computed
	 *
	 * @return an unsigned atom
	 */
	public Atom buildAtomWithFee(@Nullable BigDecimal fee) {
		// 1. Retrieve the particle groups (without removing them from store)
		// 2. Process fees. During processing more stages can be submitted into the store
		// 3. Build final atom with fees and remove particle groups from store
		// TODO: replace this interlinked/overcomplicated logic with direct transformation from fee less atom to atom

		var noFeeAtom = Atom.create(api.getAtomStore().getStaged(uuid), message);
		feeProcessor.process(this::actionProcessor, api.getAddress(), noFeeAtom, ofNullable(fee));

		var messageCopy = message;
		message = null;

		return Atom.create(api.getAtomStore().getStagedAndClear(uuid), messageCopy);
	}

	/**
	 * Creates an atom composed of all of the currently staged particles.
	 *
	 * @return an unsigned atom
	 */
	public Atom buildAtom() {
		return buildAtomWithFee(null);
	}

	/**
	 * Commit the transaction onto the ledger.
	 * If the specified fee is non-null, a fee of that amount will be included in
	 * the built atom, otherwise the fee will be computed based on the atom properties.
	 *
	 * @param fee the fee to include in the atom, or {@code null} if the fee should be computed
	 *
	 * @return the results of committing
	 */
	public Result commitAndPushWithFee(@Nullable BigDecimal fee) {
		final var atom = api.getIdentity().addSignature(buildAtomWithFee(fee));

		return api.createAtomSubmission(atom, false, null).connect();
	}

	/**
	 * Commit the transaction onto the ledger. Fee particles will be added to the atom.
	 *
	 * @return the results of committing
	 */
	public Result commitAndPush() {
		return commitAndPushWithFee(null);
	}

	/**
	 * Commit the transaction onto the ledger. No fee particles will be added.
	 *
	 * @return the results of committing
	 */
	public Result commitAndPushWithoutFee() {
		return commitAndPushWithFee(BigDecimal.ZERO);
	}

	/**
	 * Commit the transaction onto the ledger via the specified node.
	 * If the specified fee is non-null, a fee of that amount will be included in
	 * the built atom, otherwise the fee will be computed based on the atom properties.
	 *
	 * @param originNode the originNode to push to
	 * @param fee the fee to include in the atom, or {@code null} if the fee should be computed
	 *
	 * @return the results of committing
	 */
	public Result commitAndPushWithFee(RadixNode originNode, @Nullable BigDecimal fee) {
		final var atom = api.getIdentity().addSignature(buildAtomWithFee(fee));

		return api.createAtomSubmission(atom, false, originNode).connect();
	}

	/**
	 * Commit the transaction onto the ledger
	 *
	 * @param originNode the originNode to push to
	 *
	 * @return the results of committing
	 */
	public Result commitAndPush(RadixNode originNode) {
		return commitAndPushWithFee(originNode, null);
	}

	/**
	 * Gets the unique identifier of this transaction
	 *
	 * @return the unique identifier of this transaction
	 */
	public String getUuid() {
		return uuid;
	}

	private void actionProcessor(Action action) {
		stage(action);
	}
}
