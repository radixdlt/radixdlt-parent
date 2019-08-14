package com.radixdlt.atommodel.tokens;

import static com.radixdlt.atomos.Result.error;
import static com.radixdlt.atomos.Result.of;
import static com.radixdlt.atomos.Result.success;

import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Objects;

public enum TokenPermission {
	/**
	 * Only the token owner can do this
	 */
	TOKEN_OWNER_ONLY((tokDefRef, meta) -> of(
		meta.isSignedBy(tokDefRef.getAddress()),
		() -> "must be signed by token owner: " + tokDefRef.getAddress())),

	/**
	 * Everyone can do this
	 */
	ALL((token, meta) -> success()),

	/**
	 * No-one can do this
	 */
	NONE((token, meta) -> error("no-one can do this"));

	private final TokenPermissionCheck check;

	TokenPermission(TokenPermissionCheck check) {
		this.check = Objects.requireNonNull(check);
	}

	/**
	 * Check whether this permissions allows an action of the definition in a specific atom
	 * @param tokDefRef the token reference
	 * @param meta the metadata of the containing atom
	 * @return the result of this check
	 */
	public Result check(RRI tokDefRef, AtomMetadata meta) {
		return check.check(tokDefRef, meta);
	}

	/**
	 * Internal interface for cleaner permission definition
	 */
	@FunctionalInterface
	private interface TokenPermissionCheck {
		Result check(RRI tokDefRef, AtomMetadata meta);
	}
}
