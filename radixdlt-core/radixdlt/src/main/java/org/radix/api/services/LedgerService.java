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

package org.radix.api.services;

import org.json.JSONObject;

import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.StoreIndex;

import static org.radix.api.jsonrpc.AtomStatus.DOES_NOT_EXIST;
import static org.radix.api.jsonrpc.AtomStatus.STORED;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.response;

import static com.radixdlt.middleware2.store.EngineAtomIndices.IndexType.DESTINATION;
import static com.radixdlt.serialization.DsonOutput.Output.API;
import static com.radixdlt.store.LedgerSearchMode.EXACT;
import static com.radixdlt.store.StoreIndex.LedgerIndexType.DUPLICATE;

public class LedgerService {
	private final LedgerEntryStore ledger;
	private final Serialization serialization;

	public LedgerService(final LedgerEntryStore ledger, final Serialization serialization) {
		this.ledger = ledger;
		this.serialization = serialization;
	}

	public JSONObject getAtomStatus(final JSONObject request, final String aidStr) {
		var atomStatus = ledger.contains(AID.from(aidStr)) ? STORED : DOES_NOT_EXIST;

		return response(request, jsonObject().put("status", atomStatus.toString()));
	}

	public JSONObject getAtoms(final JSONObject request, final String addressString) {
		var address = RadixAddress.from(addressString);
		var index = new StoreIndex(DESTINATION.getValue(), address.euid().toByteArray());
		var cursor = ledger.search(DUPLICATE, index, EXACT);
		var result = jsonArray();

		while (cursor != null) {
			result.put(serialization.toJsonObject(cursor.get(), API));
			cursor = cursor.next();
		}

		return response(request, result);
	}
}
