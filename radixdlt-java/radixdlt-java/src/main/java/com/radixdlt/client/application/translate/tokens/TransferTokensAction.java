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

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

import java.math.BigDecimal;
import java.util.Objects;

public class TransferTokensAction implements Action {
	private final RadixAddress from;
	private final RadixAddress to;
	private final RRI rri;
	private final BigDecimal amount;
	private final byte[] attachment;

	private TransferTokensAction(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment
	) {
		this.from = from;
		this.to = to;
		this.rri = rri;
		this.amount = amount;
		this.attachment = attachment;
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment
	) {
		Objects.requireNonNull(rri);
		Objects.requireNonNull(from);
		Objects.requireNonNull(to);
		Objects.requireNonNull(amount);

		if (amount.stripTrailingZeros().scale() > TokenUnitConversions.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenUnitConversions.getTokenScale());
		}

		return new TransferTokensAction(rri, from, to, amount, attachment);
	}

	public static TransferTokensAction create(RRI rri, RadixAddress from, RadixAddress to, BigDecimal amount) {
		return create(rri, from, to, amount, null);
	}

	public byte[] getAttachment() {
		return attachment;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RRI getRRI() {
		return rri;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return "TRANSFER TOKEN " + amount + " " + rri.getName() + " FROM " + from + " TO " + to;
	}
}
