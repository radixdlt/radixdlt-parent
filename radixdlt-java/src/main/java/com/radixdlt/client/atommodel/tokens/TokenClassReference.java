package com.radixdlt.client.atommodel.tokens;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.ParticleIndex;

@SerializerId2("TOKENCLASSREFERENCE")
public final class TokenClassReference extends ParticleIndex {
	private static final Charset CHARSET = StandardCharsets.UTF_8;

	/**
	 * Number of subunits in a unit as a power of 10, currently {@value #SUB_UNITS_POW_10}.
	 * In other words, the total number of subunits per unit is 10<sup>{@code SUB_UNITS_POW_10}</sup>.
	 */
	public static final int SUB_UNITS_POW_10 = 18;
	/**
	 * Number of subunits per unit.
	 * @see #SUB_UNITS_POW_10
	 */
	public static final UInt256 SUB_UNITS = UInt256.TEN.pow(SUB_UNITS_POW_10);
	private static final BigDecimal SUB_UNITS_BIG_DECIMAL = UInt256s.toBigDecimal(SUB_UNITS);

	public static int getTokenScale() {
		return SUB_UNITS_POW_10;
	}

	public static BigDecimal getSubunits() {
		return SUB_UNITS_BIG_DECIMAL;
	}

	/**
	 * Returns the specified number of subunits as a fractional number
	 * of units.  This method effectively calculates:
	 * <blockquote>
	 *    <var>subunits</var> &times; 10<sup>-SUB_UNITS_POW_10</sup>
	 * </blockquote>
	 *
	 * @param subunits The number of subunits to convert to fractional units
	 * @return The number of fractional units represented by {@code subunits}
	 * @see #SUB_UNITS_POW_10
	 */
	public static BigDecimal subunitsToUnits(UInt256 subunits) {
		return subunitsToUnits(UInt256s.toBigInteger(subunits));
	}

	/**
	 * Returns the specified number of subunits as a fractional number
	 * of units.  This method effectively calculates:
	 * <blockquote>
	 *    <var>subunits</var> &times; 10<sup>-SUB_UNITS_POW_10</sup>
	 * </blockquote>
	 *
	 * @param subunits The number of subunits to convert to fractional units
	 * @return The number of fractional units represented by {@code subunits}
	 * @see #SUB_UNITS_POW_10
	 */
	public static BigDecimal subunitsToUnits(BigInteger subunits) {
		return new BigDecimal(subunits, SUB_UNITS_POW_10);
	}

	/**
	 * Returns the specified number of subunits as a fractional number
	 * of units.  This method effectively calculates:
	 * <blockquote>
	 *    <var>subunits</var> &times; 10<sup>-SUB_UNITS_POW_10</sup>
	 * </blockquote>
	 *
	 * @param subunits The number of subunits to convert to fractional units
	 * @return The number of fractional units represented by {@code subunits}
	 * @see #SUB_UNITS_POW_10
	 */
	public static BigDecimal subunitsToUnits(long subunits) {
		return BigDecimal.valueOf(subunits, SUB_UNITS_POW_10);
	}

	/**
	 * Returns the specified number of units as a {@link UInt256} number of
	 * of subunits.  This method effectively calculates:
	 * <blockquote>
	 *    <var>units</var> &times; 10<sup>SUB_UNITS_POW_10</sup>
	 * </blockquote>
	 *
	 * @param units The number of units to convert to subunits
	 * @return The integer number of subunits represented by {@code units}
	 * @throws IllegalArgumentException if {@code units} is less than zero
	 * @see #SUB_UNITS_POW_10
	 */
	public static UInt256 unitsToSubunits(long units) {
		if (units < 0) {
			throw new IllegalArgumentException("units must be >= 0: " + units);
		}
		// 10^18 is approximately 60 bits, so a positive long (63 bits) cannot overflow here
		return UInt256.from(units).multiply(SUB_UNITS);
	}

	/**
	 * Returns the specified number of units as a {@link UInt256} number of
	 * of subunits.  This method effectively calculates:
	 * <blockquote>
	 *    <var>units</var> &times; 10<sup>SUB_UNITS_POW_10</sup>
	 * </blockquote>
	 *
	 * @param units The number of units to convert to subunits
	 * @return The integer number of subunits represented by {@code units}
	 * @throws IllegalArgumentException if {@code units} is less than zero
	 *         or greater than {@link UInt256#MAX_VALUE}
     * @throws ArithmeticException if {@code units} &times;
     *         10<sup>SUB_UNITS_POW_10</sup> has a nonzero fractional part.
	 * @see #SUB_UNITS_POW_10
	 */
	public static UInt256 unitsToSubunits(BigDecimal units) {
		return UInt256s.fromBigDecimal(units.multiply(SUB_UNITS_BIG_DECIMAL));
	}

	@JsonProperty("symbol")
	@DsonOutput(Output.ALL)
	private String symbol;

	TokenClassReference() {
		// No-arg constructor for serializer
	}

	private TokenClassReference(RadixAddress address, String symbol) {
		super(address);
		Objects.requireNonNull(symbol);

		this.symbol = symbol;
	}

	public static TokenClassReference of(RadixAddress address, String reference) {
		return new TokenClassReference(address, reference);
	}

	public String getSymbol() {
		return symbol;
	}

	public static EUID calcEUID(String isoCode) {
		return RadixHash.of(isoCode.getBytes(CHARSET)).toEUID();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenClassReference)) {
			return false;
		}

		TokenClassReference tokenClassReference = (TokenClassReference) o;
		return this.symbol.equals(tokenClassReference.symbol) && this.getAddress().equals(tokenClassReference.getAddress());
	}

	@Override
	public int hashCode() {
		return toString().hashCode(); //FIXME: quick hack for now
	}

	@Override
	public String toString() {
		return String.format("%s/@%s", this.getAddress().toString(), symbol);
	}
}
