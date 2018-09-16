package com.radixdlt.client.core.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import org.junit.Test;

public class RadixNetworkTest {

	@Test
	public void testGetClientsMultipleTimes() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixNetwork network = new RadixNetwork(config, () -> Observable.just(
			new RadixPeer("1", false, 8080),
			new RadixPeer("2", false, 8080),
			new RadixPeer("3", false, 8080)
		));

		IntStream.range(0, 10).forEach(i ->
			network.getRadixClients()
				.map(RadixJsonRpcClient::getLocation)
				.test()
				.assertValueAt(0, "http://1:8080/rpc")
				.assertValueAt(1, "http://2:8080/rpc")
				.assertValueAt(2, "http://3:8080/rpc")
		);
	}

	@Test
	public void testAPIMismatch() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(peer.servesShards(any())).thenReturn(Maybe.just(peer));
		when(peer.getRadixClient()).thenReturn(client);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(false));

		RadixNetwork network = new RadixNetwork(config, () -> Observable.just(peer));

		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		network.getRadixClient(0L).subscribe(testObserver);
		testObserver.assertError(NoSuchElementException.class);
	}

	@Test
	public void testUniverseMismatch() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(peer.servesShards(any())).thenReturn(Maybe.just(peer));
		when(peer.getRadixClient()).thenReturn(client);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(true));
		RadixUniverseConfig config2 = mock(RadixUniverseConfig.class);
		when(client.getUniverse()).thenReturn(Single.just(config2));

		RadixNetwork network = new RadixNetwork(config, () -> Observable.just(peer));

		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		network.getRadixClient(0L).subscribe(testObserver);
		testObserver.assertError(NoSuchElementException.class);
	}

	@Test
	public void testValidClient() {
		RadixUniverseConfig config = mock(RadixUniverseConfig.class);
		RadixPeer peer = mock(RadixPeer.class);
		RadixJsonRpcClient client = mock(RadixJsonRpcClient.class);
		when(peer.servesShards(any())).thenReturn(Maybe.just(peer));
		when(peer.getRadixClient()).thenReturn(client);
		when(client.getStatus()).thenReturn(Observable.just(RadixClientStatus.OPEN));
		when(client.checkAPIVersion()).thenReturn(Single.just(true));
		when(client.getUniverse()).thenReturn(Single.just(config));

		RadixNetwork network = new RadixNetwork(config, () -> Observable.just(peer));

		TestObserver<RadixJsonRpcClient> testObserver = TestObserver.create();
		network.getRadixClient(0L).subscribe(testObserver);
		testObserver.assertValue(client);
	}
}