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

package com.radixdlt.client.application.translate;

import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import static com.radixdlt.client.application.translate.tokens.TokenUnitConversions.subunitsToUnits;


/**
 * Maps a complete list of particles ready to be submitted to a token fee particle group.
 */
public final class TokenFeeProcessor implements FeeProcessor {
	private final RRI tokenRri;
	private final FeeTable feeTable;

	/**
	 * Processes fees for supplied atoms.
	 *
	 * @param tokenRri The RRI of the token to use for paying fees
	 * @param feeTable The {@link FeeTable} to use for calculating fees
	 */
	public TokenFeeProcessor(RRI tokenRri, FeeTable feeTable) {
		this.tokenRri = Objects.requireNonNull(tokenRri);
		this.feeTable = Objects.requireNonNull(feeTable);
	}

	@Override
	public void process(ActionProcessor actionProcessor, RadixAddress address, Atom noFeeAtom, Optional<BigDecimal> optionalFee) {
		var feeToPay = optionalFee.orElseGet(() -> subunitsToUnits(this.feeTable.feeFor(noFeeAtom)));

		switch (feeToPay.signum()) {
			default:
				return;
			case -1:
				throw new IllegalArgumentException("Token fee must be greater than or equal to zero: " + feeToPay);
			case 1:
				actionProcessor.process(BurnTokensAction.create(tokenRri, address, feeToPay));
				break;
		}
	}
}
