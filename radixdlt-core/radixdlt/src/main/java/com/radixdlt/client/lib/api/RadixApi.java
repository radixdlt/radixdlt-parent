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

package com.radixdlt.client.lib.api;

import com.radixdlt.client.Rri;
import com.radixdlt.client.lib.dto.NetworkIdDTO;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TokenInfoDTO;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistoryDTO;
import com.radixdlt.client.lib.impl.SynchronousRadixApiClient;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;

public interface RadixApi {
	static Result<RadixApi> connect(String baseUrl) {
		return SynchronousRadixApiClient.connect(baseUrl);
	}

	Result<NetworkIdDTO> networkId();
	Result<TokenInfoDTO> nativeToken();
	Result<TokenInfoDTO> tokenInfo(String rri);
	Result<TokenBalancesDTO> tokenBalances(REAddr address);
	Result<TransactionHistoryDTO> transactionHistory(REAddr address);
	Result<TransactionDTO> lookupTransaction(AID txId);
	//Result<> stakePositions(REAddr address);
	//Result<> unstakePositions(REAddr address);
	//Result<> statusOfTransaction(AID txId);
	//Result<> networkTransactionThroughput();
	//Result<> networkTransactionDemand();
	//Result<> validators();
	//Result<> lookupValidator();
	//Result<> buildTransaction();
	//Result<> finalizeTransaction();
	//Result<> submitTransaction();


}
