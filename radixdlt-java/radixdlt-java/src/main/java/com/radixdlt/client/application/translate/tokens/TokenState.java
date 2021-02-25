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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The state and data of a token at a given moment in time
 */
public class TokenState {
	public enum TokenSupplyType {
		FIXED,
		MUTABLE
	}

	private final String name;
	private final String iso;
	private final String description;
	private final String iconUrl;
	private final BigDecimal totalSupply;
	private final BigDecimal granularity;
	private final TokenSupplyType tokenSupplyType;

	public TokenState(
		String name,
		String iso,
		String description,
		String iconUrl,
		BigDecimal totalSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		this.name = name;
		this.iso = iso;
		this.description = description;
		this.iconUrl = iconUrl;
		this.totalSupply = totalSupply;
		this.granularity = granularity;
		this.tokenSupplyType = tokenSupplyType;
	}

	public static TokenState combine(TokenState state0, TokenState state1) {
		return new TokenState(
			coalesce(state0.name, state1.name),
			coalesce(state0.iso, state1.iso),
			coalesce(state0.description, state1.description),
			coalesce(state0.iconUrl, state1.iconUrl),
			calculateTotalSupply(state0, state1),
			coalesce(state0.granularity, state1.granularity),
			coalesce(state0.tokenSupplyType, state1.tokenSupplyType)
		);
	}

	private static BigDecimal calculateTotalSupply(final TokenState state0, final TokenState state1) {
		if (state0.totalSupply == null) {
			return state1.totalSupply;
		}

		return state1.totalSupply != null
			   ? state0.totalSupply.add(state1.totalSupply)
			   : state0.totalSupply;
	}

	private static <T> T coalesce(final T v1, final T v2) {
		return v1 != null ? v1 : v2;
	}

	public String getName() {
		return name;
	}

	public String getIso() {
		return iso;
	}

	public String getDescription() {
		return description;
	}

	public String getIconUrl() {
		return iconUrl;
	}

	public BigDecimal getTotalSupply() {
		return totalSupply;
	}

	public BigDecimal getGranularity() {
		return this.granularity;
	}

	public TokenSupplyType getTokenSupplyType() {
		return tokenSupplyType;
	}

	public BigDecimal getMaxSupply() {
		return totalSupply;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, iso, description, tokenSupplyType, totalSupply);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenState)) {
			return false;
		}

		var tokenState = (TokenState) o;
		return Objects.equals(this.name, tokenState.name)
			&& Objects.equals(this.iso, tokenState.iso)
			&& Objects.equals(this.tokenSupplyType, tokenState.tokenSupplyType)
			&& Objects.equals(this.description, tokenState.description)
			// Note BigDecimal.equal does not return true for different scales
			&& Objects.compare(this.granularity, tokenState.granularity, BigDecimal::compareTo) == 0
			&& Objects.compare(this.totalSupply, tokenState.totalSupply, BigDecimal::compareTo) == 0;
	}

	@Override
	public String toString() {
		return String.format(
			"Token(%s:%s) name(%s) description(%s) url(%s) totalSupply(%s) granularity(%s)",
			this.iso,
			this.tokenSupplyType,
			this.name,
			this.description,
			this.iconUrl,
			this.totalSupply,
			this.granularity
		);
	}
}
