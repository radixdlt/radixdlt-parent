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

package com.radixdlt.client.core.network.jsonrpc;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.serialization.GsonJson;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput.Output;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.reactivex.functions.Cancellable;
import io.reactivex.observers.TestObserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixJsonRpcClientTest {

	@Test
	public void getSelfTestError() {
		var channel = mock(PersistentChannel.class);

		when(channel.sendMessage(any())).thenReturn(false);

		var jsonRpcClient = new RadixJsonRpcClient(channel);
		var observer = new TestObserver<NodeRunnerData>();

		jsonRpcClient.getInfo().subscribe(observer);

		observer.assertValueCount(0);
		observer.assertError(t -> true);
	}

	@Test
	public void getAtomDoesNotExistTest() {
		var channel = mock(PersistentChannel.class);

		var listener = new AtomicReference<Consumer<String>>();

		doAnswer(a -> {
			Consumer<String> l = a.getArgument(0);
			listener.set(l);
			return mock(Cancellable.class);
		}).when(channel).addListener(any());

		var parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			var msg = (String) invocation.getArguments()[0];
			var jsonObject = parser.parse(msg).getAsJsonObject();
			var id = jsonObject.get("id").getAsString();

			var atoms = new JsonArray();

			var response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			listener.get().accept(GsonJson.getInstance().stringFromGson(response));
			return true;
		});

		var jsonRpcClient = new RadixJsonRpcClient(channel);
		var observer = new TestObserver<Atom>();

		jsonRpcClient.getAtom(new EUID(1)).subscribe(observer);

		observer.assertValueCount(0);
		observer.assertComplete();
		observer.assertNoErrors();
	}

	@Test
	public void getAtomTest() {
		var channel = mock(PersistentChannel.class);
		var listener = new AtomicReference<Consumer<String>>();

		doAnswer(a -> {
			Consumer<String> l = a.getArgument(0);
			listener.set(l);
			return mock(Cancellable.class);
		}).when(channel).addListener(any());

		var parser = new JsonParser();

		when(channel.sendMessage(any())).then(invocation -> {
			var msg = (String) invocation.getArguments()[0];
			var jsonObject = parser.parse(msg).getAsJsonObject();
			var id = jsonObject.get("id").getAsString();

			var atoms = new JsonArray();
			var atom = Atom.create();
			var atomJson = Serialize.getInstance().toJson(atom, Output.API);
			atoms.add(parser.parse(atomJson));

			var response = new JsonObject();
			response.addProperty("id", id);
			response.add("result", atoms);

			listener.get().accept(GsonJson.getInstance().stringFromGson(response));
			return true;
		});

		var jsonRpcClient = new RadixJsonRpcClient(channel);
		var observer = new TestObserver<Atom>();

		jsonRpcClient.getAtom(new EUID(1)).subscribe(observer);

		observer.assertValueCount(1);
		observer.assertComplete();
		observer.assertNoErrors();
	}
}
