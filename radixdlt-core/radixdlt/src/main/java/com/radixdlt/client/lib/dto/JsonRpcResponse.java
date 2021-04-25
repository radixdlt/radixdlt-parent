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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

public class JsonRpcResponse<T> {
	private final String version;
	private final String id;
	private final T result;
	private final ErrorInfo error;

	private JsonRpcResponse(String version, String id, T result, ErrorInfo error) {
		this.version = version;
		this.id = id;
		this.result = result;
		this.error = error;
	}

	@JsonCreator
	public static <T> JsonRpcResponse<T> create(
		@JsonProperty(value = "jsonrpc", required = true) String version,
		@JsonProperty(value = "id", required = true) String id,
		@JsonProperty("result") T result,
		@JsonProperty("error") ErrorInfo error
	) {
		return new JsonRpcResponse<>(version, id, result, error);
	}

	public Optional<ErrorInfo> error() {
		return Optional.ofNullable(error);
	}

	public Optional<T> result() {
		return Optional.ofNullable(result);
	}

	public String getVersion() {
		return version;
	}

	public String getId() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof JsonRpcResponse)) {
			return false;
		}

		var that = (JsonRpcResponse<?>) o;
		return version.equals(that.version)
			&& id.equals(that.id)
			&& Objects.equals(result, that.result)
			&& Objects.equals(error, that.error);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, id, result, error);
	}
}
