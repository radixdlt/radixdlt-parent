/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.api.data.ActionType;
import com.radixdlt.api.data.TransactionAction;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.JsonRpcUtil.withRequiredParameters;

import static java.util.Optional.empty;

public class FaucetHandler {
	private static final Logger logger = LogManager.getLogger();
	private static final UInt256 AMOUNT = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	private final SubmissionService submissionService;
	private final REAddr account;
	private final Set<REAddr> tokensToSend;
	private final HashSigner hashSigner;

	@Inject
	public FaucetHandler(
		SubmissionService submissionService,
		REAddr account, Set<REAddr> tokensToSend,
		@Named("RadixEngine") HashSigner hashSigner
	) {
		this.submissionService = submissionService;
		this.account = account;
		this.tokensToSend = tokensToSend;
		this.hashSigner = hashSigner;
	}

	public JSONObject requestTokens(JSONObject request) {
		return withRequiredParameters(
			request,
			List.of("address"),
			List.of(),
			params -> parseAddress(params).flatMap(this::sendTokens)
		);
	}

	private Result<JSONObject> sendTokens(REAddr destination) {
		logger.info("Sending {} {} to {}", AMOUNT, tokensToSend, AccountAddress.of(destination));

		var steps = new ArrayList<TransactionAction>();

		tokensToSend.forEach(rri -> steps.add(transfer(destination, rri)));

		return submissionService.oneStepSubmit(steps, empty(), hashSigner).map(FaucetHandler::formatTxId);
	}

	private TransactionAction transfer(REAddr destination, REAddr rri) {
		return TransactionAction.create(ActionType.TRANSFER, account, destination, AMOUNT, Optional.of(rri));
	}

	private Result<REAddr> parseAddress(JSONObject params) {
		return AccountAddress.parseFunctional(params.getString("address"));
	}

	private static JSONObject formatTxId(AID txId) {
		return jsonObject().put("txID", txId);
	}
}