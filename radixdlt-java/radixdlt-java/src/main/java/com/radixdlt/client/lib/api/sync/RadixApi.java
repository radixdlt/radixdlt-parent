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

package com.radixdlt.client.lib.api.sync;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.ApiConfiguration;
import com.radixdlt.client.lib.dto.ApiData;
import com.radixdlt.client.lib.dto.BuiltTransaction;
import com.radixdlt.client.lib.dto.Checkpoint;
import com.radixdlt.client.lib.dto.ConsensusConfiguration;
import com.radixdlt.client.lib.dto.ConsensusData;
import com.radixdlt.client.lib.dto.EpochData;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.ForkDetails;
import com.radixdlt.client.lib.dto.LocalAccount;
import com.radixdlt.client.lib.dto.LocalValidatorInfo;
import com.radixdlt.client.lib.dto.MempoolConfiguration;
import com.radixdlt.client.lib.dto.MempoolData;
import com.radixdlt.client.lib.dto.NetworkConfiguration;
import com.radixdlt.client.lib.dto.NetworkData;
import com.radixdlt.client.lib.dto.NetworkId;
import com.radixdlt.client.lib.dto.NetworkPeer;
import com.radixdlt.client.lib.dto.NetworkStats;
import com.radixdlt.client.lib.dto.Proof;
import com.radixdlt.client.lib.dto.RadixEngineData;
import com.radixdlt.client.lib.dto.StakePositions;
import com.radixdlt.client.lib.dto.SyncConfiguration;
import com.radixdlt.client.lib.dto.SyncData;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.client.lib.dto.TokenInfo;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.client.lib.dto.TransactionStatusDTO;
import com.radixdlt.client.lib.dto.TxBlobDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import com.radixdlt.client.lib.dto.UnstakePositions;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.client.lib.dto.ValidatorsResponse;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.functional.Result;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * <h2>Synchronous Radix JSON RPC client.</h2>
 * <p>
 * The Radix Web API consists of several endpoints which are assigned to two large groups. Each group served by
 * dedicated embedded HTTP server hence full configuration of the client requires base URL and two ports.
 * <p>
 * Each endpoint can be individually enabled or disabled, so even if client is successfully connected, it does not
 * mean that all API's are available. This should be kept in mind while using client with particular hode.
 * <p>
 * <h3>Client API structure</h3>
 * API is split into following groups:
 * <p>
 * <table>
 * <caption style="display:none">apiGroups</caption>
 * <tbody>
 *     <tr><th>Name</th><th>Description</th></tr>
 *     <tr><td>Network</td><td>General information about network: ID, configuration, nodes, etc.</td></tr>
 *     <tr><td>Transaction</td><td>General purpose API for building and sending transactions, checking status, etc.</td></tr>
 *     <tr><td>Token</td><td>Information about tokens</td></tr>
 *     <tr><td>Local</td><td>Information about the node as well as single step transaction submission</td></tr>
 *     <tr><td>SingleAccount</td><td>Information related to single account: balances, transaction history, etc.</td></tr>
 *     <tr><td>Validator</td><td>List and lookup information about validators known to network</td></tr>
 *     <tr><td>Api</td><td>API configuration and metric counters</td></tr>
 *     <tr><td>Consensus</td><td>Consensus configuration and metric counters</td></tr>
 *     <tr><td>Mempool</td><td>Mempool configuration and metric counters</td></tr>
 *     <tr><td>RadixEngine</td><td>Radix Engine configuration and metric counters</td></tr>
 *     <tr><td>Sync</td><td>Node synchronization configuration and metric counters</td></tr>
 *     <tr><td>Ledger</td><td>Ledger proofs and checkpoints information</td></tr>
 * </tbody>
 * </table>
 */
public interface RadixApi {
	int DEFAULT_PRIMARY_PORT = 8080;
	int DEFAULT_SECONDARY_PORT = 3333;

	/**
	 * Create client and connect to specified node.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 *
	 * @return {@link Result} which will contain built client or error info.
	 */
	static Result<RadixApi> connect(String baseUrl) {
		return connect(baseUrl, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT);
	}

	/**
	 * Create client and connect to specified node at specified primary and secondary ports.
	 *
	 * @param baseUrl base URL to connect. Note that it should not include path part of the URL.
	 *
	 * @return {@link Result} which will contain built client or error info.
	 */
	static Result<RadixApi> connect(String baseUrl, int primaryPort, int secondaryPort) {
		return SyncRadixApi.connect(baseUrl, primaryPort, secondaryPort);
	}

	/**
	 * Enable tracing in client.
	 */
	RadixApi withTrace();

	/**
	 * Configure timeout for network operations.
	 *
	 * @param timeout - operation timeout
	 */
	RadixApi withTimeout(Duration timeout);

	/**
	 * Network API's
	 */
	interface Network {
		/**
		 * Get network ID.
		 */
		Result<NetworkId> id();

		/**
		 * Get current network throughput in transactions per second.
		 */
		Result<NetworkStats> throughput();

		/**
		 * Get current network demand in transactions per second.
		 */
		Result<NetworkStats> demand();

		/**
		 * Get current network configuration.
		 */
		Result<NetworkConfiguration> configuration();

		/**
		 * Get network metrics.
		 */
		Result<NetworkData> data();

		/**
		 * Get network peers.
		 */
		Result<List<NetworkPeer>> peers();
	}

	Network network();

	/**
	 * Transaction API's.
	 * <p>
	 * Radix API uses three step transaction submission:
	 * <ol>
	 *     <li>Build - transaction blob is assembled from the high level action description</li>
	 *     <li>Finalize - transaction is prepared, validated, transaction ID is calculated and returned</li>
	 *     <li>Submit - transaction is actually submitted to mempool</li>
	 * </ol>
	 * This process is designed for the case of very unreliable communication, to prevent double submission and
	 * other potential issues. If this is less of an issue in particular use case, it is possible to omit last
	 * step and submit transaction during finalization step. To achieve this, set {@code immediateSubmit} flag
	 * in {@link #finalize(FinalizedTransaction, boolean)} to {@code true}.
	 */
	interface Transaction {
		/**
		 * Build transaction for a given transaction request.
		 *
		 * @param request transaction request
		 */
		Result<BuiltTransaction> build(TransactionRequest request);

		/**
		 * Finalize transaction.
		 *
		 * @param request transaction request (can be built from {@link BuiltTransaction} by invoking {@link BuiltTransaction#toFinalized(ECKeyPair)}
		 * 	method)
		 * @param immediateSubmit if set to {@code true} then transaction will be immediately submitted to mempool
		 */
		Result<TxBlobDTO> finalize(FinalizedTransaction request, boolean immediateSubmit);

		/**
		 * Submit transaction.
		 *
		 * @param request transaction request
		 */
		Result<TxDTO> submit(TxBlobDTO request);

		/**
		 * Lookup transaction.
		 *
		 * @param txId the ID of the transaction to look up
		 */
		Result<TransactionDTO> lookup(AID txId);

		/**
		 * Get transaction status.
		 *
		 * @param txId the ID of the transaction to get status for
		 */
		Result<TransactionStatusDTO> status(AID txId);
	}

	Transaction transaction();

	/**
	 * Token-related API's
	 */
	interface Token {
		/**
		 * Get description of the native token.
		 */
		Result<TokenInfo> describeNative();

		/**
		 * Get description of the token with a given RRI.
		 */
		Result<TokenInfo> describe(String rri);
	}

	Token token();

	/**
	 * API's which deal with information local to node to which client is connected.
	 * <p>
	 * <b>WARNING:</b> These API's may expose or use security-sensitive information. Use with care.
	 */
	interface Local {
		/**
		 * Get local node account information.
		 */
		Result<LocalAccount> accountInfo();

		/**
		 * Submit transaction is single step, using local node private key to sign the transaction.
		 *
		 * @param request high level action description
		 */
		Result<TxDTO> submitTxSingleStep(TransactionRequest request);

		/**
		 * Get information about local node as a validator.
		 */
		Result<LocalValidatorInfo> validatorInfo();

		/**
		 * Get information about current epoch validator set.
		 */
		Result<EpochData> currentEpoch();

		/**
		 * Get information about next epoch validator set.
		 */
		Result<EpochData> nextEpoch();
	}

	Local local();

	/**
	 * Single account address API's
	 */
	interface SingleAccount {
		/**
		 * Get account balances.
		 *
		 * @param address account address for which information is requested
		 */
		Result<TokenBalances> balances(AccountAddress address);

		/**
		 * Get transaction history.
		 * <p>
		 * To get full list, pass empty cursor for first request and then just pass cursor received in the response
		 * back to API until you get empty cursor again.
		 *
		 * @param address account address for which information is requested
		 * @param size batch size
		 * @param cursor pagination cursor
		 */
		Result<TransactionHistory> history(AccountAddress address, int size, Optional<NavigationCursor> cursor);

		/**
		 * Get stakes made from given account.
		 *
		 * @param address account address for which information is requested
		 */
		Result<List<StakePositions>> stakes(AccountAddress address);

		/**
		 * Get pending (not yet transferred back) unstakes.
		 *
		 * @param address account address for which information is requested
		 */
		Result<List<UnstakePositions>> unstakes(AccountAddress address);
	}

	SingleAccount account();

	/**
	 * General validator information API's
	 */
	interface Validator {
		/**
		 * Get paginated list of all validators known to the network.
		 * <p>
		 * To get full list, pass empty cursor for first request and then just pass cursor received in the response
		 * back to API until you get empty cursor again.
		 *
		 * @param size batch size
		 * @param cursor pagination cursor
		 */
		Result<ValidatorsResponse> list(int size, Optional<NavigationCursor> cursor);

		/**
		 * Lookup validator by address.
		 *
		 * @param validatorAddress
		 */
		Result<ValidatorDTO> lookup(ValidatorAddress validatorAddress);
	}

	Validator validator();

	/**
	 * Node API configuration and metrics.
	 */
	interface Api {
		/**
		 * Get API configuration.
		 */
		Result<ApiConfiguration> configuration();

		/**
		 * Get API metrics.
		 */
		Result<ApiData> data();
	}

	Api api();

	/**
	 * Consensus configuration and metrics.
	 */
	interface Consensus {
		/**
		 * Get consensus configuration.
		 */
		Result<ConsensusConfiguration> configuration();

		/**
		 * Get consensus metrics.
		 */
		Result<ConsensusData> data();
	}

	Consensus consensus();

	/**
	 * Mempool configuration and metrics.
	 */
	interface Mempool {
		/**
		 * Get mempool configuration.
		 */
		Result<MempoolConfiguration> configuration();

		/**
		 * Get mempool metrics.
		 */
		Result<MempoolData> data();
	}

	Mempool mempool();

	/**
	 * RadixEngine configuration and metrics.
	 */
	interface RadixEngine {
		/**
		 * Get Radix Engine configuration.
		 */
		Result<List<ForkDetails>> configuration();

		/**
		 * Get Radix Engine metrics.
		 */
		Result<RadixEngineData> data();
	}

	RadixEngine radixEngine();

	/**
	 * Inter-node synchronization configuration and metrics.
	 */
	interface Sync {
		/**
		 * Get synchronization configuration.
		 */
		Result<SyncConfiguration> configuration();

		/**
		 * Get synchronization metrics.
		 */
		Result<SyncData> data();
	}

	Sync sync();

	/**
	 * Ledger API's.
	 */
	interface Ledger {
		/**
		 * Get latest proof.
		 */
		Result<Proof> latest();

		/**
		 * Get latest epoch proof.
		 */
		Result<Proof> epoch();

		/**
		 * Get checkpoint configuration.
		 */
		Result<Checkpoint> checkpoints();
	}

	Ledger ledger();
}
