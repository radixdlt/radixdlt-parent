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

package com.radixdlt.atommodel.tokens.scrypt;

import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.Unknown;
import com.radixdlt.atom.actions.DeprecatedUnstakeTokens;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.DeprecatedStake;
import com.radixdlt.atommodel.tokens.Fungible;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.ParticleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.constraintmachine.DownProcedure;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ReducerResult2;
import com.radixdlt.constraintmachine.UpProcedure;
import com.radixdlt.constraintmachine.VoidReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt384;

import java.util.Objects;
import java.util.function.Function;

public final class StakingConstraintScryptV1 implements ConstraintScrypt {
	@Override
	public void main(SysCalls os) {
		os.registerParticle(
			DeprecatedStake.class,
			ParticleDefinition.<DeprecatedStake>builder()
				.staticValidation(TokenDefinitionUtils::staticCheck)
				.rriMapper(p -> REAddr.ofNativeToken())
				.build()
		);

		defineStaking(os);
	}


	private void defineStaking(SysCalls os) {
		// Stake
		os.createUpProcedure(new UpProcedure<>(
			VoidReducerState.class, DeprecatedStake.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				var state = new StakingConstraintScryptV2.UnaccountedStake(
					u,
					UInt384.from(u.getAmount())
				);
				return ReducerResult2.incomplete(state);
			}
		));
		os.createDownProcedure(new DownProcedure<>(
			TokensParticle.class, StakingConstraintScryptV2.UnaccountedStake.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> d.getSubstate().allowedToWithdraw(k, r),
			(d, s, r) -> {
				if (!d.getSubstate().getResourceAddr().isNativeToken()) {
					return ReducerResult2.error("Not the same address.");
				}
				var amt = UInt384.from(d.getSubstate().getAmount());
				var nextRemainder = s.subtract(amt);
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = s.initialParticle();
					var action = new StakeTokens(d.getSubstate().getHoldingAddr(), p.getDelegateKey(), p.getAmount());
					return ReducerResult2.complete(action);
				}

				return ReducerResult2.incomplete(nextRemainder.get());
			}
		));


		// Unstake
		os.createDownProcedure(new DownProcedure<>(
			DeprecatedStake.class, TokensConstraintScrypt.UnaccountedTokens.class,
			(d, r) -> PermissionLevel.USER,
			(d, r, k) -> k.map(d.getSubstate().getOwner()::allowToWithdrawFrom).orElse(false),
			(d, s, r) -> {
				if (!s.resourceInBucket().isNativeToken()) {
					return ReducerResult2.error("Can only destake to the native token.");
				}

				if (!Objects.equals(d.getSubstate().getOwner(), s.resourceInBucket().holdingAddress())) {
					return ReducerResult2.error("Must unstake to self");
				}

				var epochUnlocked = s.resourceInBucket().epochUnlocked();
				if (epochUnlocked.isPresent()) {
					return ReducerResult2.error("Cannot be locked for betanetV1");
				}

				var nextRemainder = s.subtract(UInt384.from(d.getSubstate().getAmount()));
				if (nextRemainder.isEmpty()) {
					// FIXME: This isn't 100% correct
					var p = (TokensParticle) s.initialParticle();
					var action = new UnstakeTokens(p.getHoldingAddr(), d.getSubstate().getDelegateKey(), p.getAmount());
					return ReducerResult2.complete(action);
				}

				if (nextRemainder.get() instanceof TokensConstraintScrypt.RemainderTokens) {
					TokensConstraintScrypt.RemainderTokens remainderTokens = (TokensConstraintScrypt.RemainderTokens) nextRemainder.get();
					var stakeRemainder = new StakingConstraintScryptV2.RemainderStake(
						remainderTokens.initialParticle(),
						remainderTokens.amount().getLow(),
						d.getSubstate().getOwner(),
						d.getSubstate().getDelegateKey()
					);
					return ReducerResult2.incomplete(stakeRemainder);
				} else {
					return ReducerResult2.incomplete(nextRemainder.get());
				}
			}
		));

		// For change
		os.createUpProcedure(new UpProcedure<>(
			StakingConstraintScryptV2.RemainderStake.class, DeprecatedStake.class,
			(u, r) -> PermissionLevel.USER,
			(u, r, k) -> true,
			(s, u, r) -> {
				if (!u.getAmount().equals(s.amount())) {
					return ReducerResult2.error("Remainder must be filled exactly.");
				}

				if (!u.getDelegateKey().equals(s.delegate())) {
					return ReducerResult2.error("Delegate key does not match.");
				}

				if (!u.getOwner().equals(s.owner())) {
					return ReducerResult2.error("Owners don't match.");
				}

				// FIXME: This isn't 100% correct
				var t = (TokensParticle) s.initialParticle();
				var action = new UnstakeTokens(t.getHoldingAddr(), u.getDelegateKey(), t.getAmount());
				return ReducerResult2.complete(action);
			}
		));
	}
}
