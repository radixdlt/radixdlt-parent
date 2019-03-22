package com.radix.regression.doublespend;

import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.RadixUniverse;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Condition;
import org.radix.common.tuples.Pair;

public class DoubleSpendTokenTransferDependencyTestConfig implements DoubleSpendTestConfig {
	private final RadixAddress apiAddress;
	private final RadixAddress toAddress;
	private final TokenDefinitionReference tokenRef;

	DoubleSpendTokenTransferDependencyTestConfig(RadixAddress apiAddress, RadixAddress toAddress) {
		this.tokenRef = TokenDefinitionReference.of(apiAddress, "JOSH");
		this.apiAddress = apiAddress;
		this.toAddress = toAddress;
	}

	@Override
	public List<Action> initialActions() {
		return Collections.singletonList(
			CreateTokenAction.create(
				apiAddress,
				"Joshy Token",
				"JOSH",
				"Cool Token",
				BigDecimal.valueOf(2),
				BigDecimal.ONE,
				TokenSupplyType.FIXED
			)
		);
	}

	@Override
	public List<List<Action>> conflictingActions() {
		return Arrays.asList(
			Arrays.asList(
				TransferTokensAction.create(apiAddress, toAddress, BigDecimal.ONE, tokenRef),
				TransferTokensAction.create(apiAddress, toAddress, BigDecimal.ONE, tokenRef)
			),
			Collections.singletonList(
				TransferTokensAction.create(apiAddress, toAddress, BigDecimal.valueOf(2), tokenRef)
			)
		);
	}

	@Override
	public PostConsensusCondition postConsensusCondition() {
		Set<Pair<String, ShardedAppStateId>> stateRequired = new HashSet<>();
		stateRequired.add(Pair.of("Balance 1", ShardedAppStateId.of(TokenBalanceState.class, apiAddress)));
		stateRequired.add(Pair.of("Balance 2", ShardedAppStateId.of(TokenBalanceState.class, toAddress)));

		return new PostConsensusCondition(
			stateRequired,
			new Condition<>(map -> {
				TokenBalanceState tokenBalanceState1 = (TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, apiAddress));
				TokenBalanceState tokenBalanceState2 = (TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, toAddress));
				return tokenBalanceState1.getBalance().get(tokenRef).getAmount().compareTo(BigDecimal.ZERO) == 0 &&
					tokenBalanceState2.getBalance().get(tokenRef).getAmount().compareTo(BigDecimal.valueOf(2)) == 0;
			}, "Transfer of 2 JOSH from one account to another")
		);
	}
}
