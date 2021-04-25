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

package com.radixdlt.client.lib.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radixdlt.client.lib.api.RadixApi;
import com.radixdlt.client.lib.dto.JsonRpcRequest;
import com.radixdlt.client.lib.dto.NetworkIdDTO;
import com.radixdlt.client.lib.dto.RpcMethod;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TokenInfoDTO;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionHistoryDTO;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import static com.radixdlt.client.lib.dto.RpcMethod.NATIVE_TOKEN;
import static com.radixdlt.client.lib.dto.RpcMethod.NETWORK_ID;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_BALANCES;
import static com.radixdlt.client.lib.dto.RpcMethod.TOKEN_INFO;
import static com.radixdlt.client.lib.dto.RpcMethod.TRANSACTION_HISTORY;
import static com.radixdlt.utils.functional.Result.fromOptional;

import static java.util.Optional.ofNullable;

public class SynchronousRadixApiClient implements RadixApi {
	private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");
	private static final ObjectMapper objectMapper;
	private final AtomicLong idCounter = new AtomicLong();

	static {
		objectMapper = new ObjectMapper();
	}

	private final String baseUrl;
	private final OkHttpClient client;
	private final AtomicReference<Byte> magicHolder = new AtomicReference<>();

	private SynchronousRadixApiClient(String baseUrl) {
		this.baseUrl = sanitize(baseUrl);
		this.client = new OkHttpClient.Builder()
			.connectionSpecs(List.of(ConnectionSpec.CLEARTEXT))
			.connectTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.pingInterval(30, TimeUnit.SECONDS)
			.build();
	}

	private static String sanitize(String baseUrl) {
		return !baseUrl.endsWith("/")
			   ? baseUrl
			   : baseUrl.substring(0, baseUrl.length() - 1);
	}

	public static Result<RadixApi> connect(String url) {
		return ofNullable(url)
			.map(baseUrl -> Result.ok(new SynchronousRadixApiClient(baseUrl)))
			.orElseGet(() -> Result.fail("Base URL is mandatory"))
			.flatMap(SynchronousRadixApiClient::tryConnect);
	}

	@Override
	public Result<NetworkIdDTO> networkId() {
		return call(request(NETWORK_ID), new TypeReference<NetworkIdDTO>() { });
	}

	@Override
	public Result<TokenInfoDTO> nativeToken() {
		return call(request(NATIVE_TOKEN), new TypeReference<TokenInfoDTO>() { });
	}

	@Override
	public Result<TokenInfoDTO> tokenInfo(String rri) {
		return call(request(TOKEN_INFO, rri), new TypeReference<TokenInfoDTO>() { });
	}

	@Override
	public Result<TokenBalancesDTO> tokenBalances(REAddr address) {
		return call(request(TOKEN_BALANCES, address.toString()), new TypeReference<TokenBalancesDTO>() { });
	}

	@Override
	public Result<TransactionHistoryDTO> transactionHistory(REAddr address) {
		return call(request(TRANSACTION_HISTORY, address.toString()), new TypeReference<TransactionHistoryDTO>() { });
	}

	@Override
	public Result<TransactionDTO> lookupTransaction(AID txId) {
		return call(request(TRANSACTION_HISTORY, txId.toString()), new TypeReference<TransactionDTO>() { });
	}

	private JsonRpcRequest request(RpcMethod method, Object... parameters) {
		return JsonRpcRequest.create(method.method(), idCounter.incrementAndGet(), parameters);
	}

	private <T> Result<T> call(JsonRpcRequest request, TypeReference<T> typeReference) {
		return serialize(request)
			.map(value -> RequestBody.create(MEDIA_TYPE, value))
			.flatMap(this::doCall)
			.flatMap(body -> deserialize(body, typeReference));
	}

	private Result<RadixApi> tryConnect() {
		return networkId()
			.map(NetworkIdDTO::getNetworkId)
			.onSuccess(magic -> magicHolder.set(magic.byteValue()))
			.map(__ -> this);
	}

	private Result<String> serialize(JsonRpcRequest request) {
		return Result.wrap(() -> objectMapper.writeValueAsString(request));
	}

	private <T> Result<T> deserialize(String body, TypeReference<T> typeReference) {
		return Result.wrap(() -> objectMapper.readValue(body, typeReference));
	}

	private Result<String> doCall(RequestBody requestBody) {
		var request = buildRequest(requestBody);

		try (var response = client.newCall(request).execute(); var responseBody = response.body()) {
			return fromOptional(ofNullable(responseBody), "No content in response")
				.flatMap(responseBody1 -> Result.wrap(responseBody1::string));
		} catch (IOException e) {
			return Result.fail(e);
		}
	}

	private Request buildRequest(RequestBody requestBody) {
		return new Request.Builder().url(baseUrl + "/rpc").post(requestBody).build();
	}
}
