/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncService;
import com.radixdlt.sync.messages.local.SyncCheckReceiveStatusTimeout;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.sync.messages.local.SyncLedgerUpdateTimeout;
import com.radixdlt.sync.messages.local.SyncRequestTimeout;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncResponse;

/**
 * A sync module which doesn't involve epochs
 */
public class NoEpochsSyncModule extends AbstractModule {

	@Override
	public void configure() {
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(SyncCheckTrigger.class);
		eventBinder.addBinding().toInstance(SyncCheckReceiveStatusTimeout.class);
		eventBinder.addBinding().toInstance(SyncRequestTimeout.class);
		eventBinder.addBinding().toInstance(LocalSyncRequest.class);
		eventBinder.addBinding().toInstance(SyncLedgerUpdateTimeout.class);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> ledgerUpdateEventProcessor(
		LocalSyncService localSyncService
	) {
		return new EventProcessorOnRunner<>(
			"sync",
			LedgerUpdate.class,
			localSyncService.ledgerUpdateEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> localSyncRequestEventProcessor(
		LocalSyncService localSyncService
	) {
		return new EventProcessorOnRunner<>(
			"sync",
			LocalSyncRequest.class,
			localSyncService.localSyncRequestEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncCheckTriggerEventProcessor(
		LocalSyncService localSyncService
	) {
		return new EventProcessorOnRunner<>(
			"sync",
			SyncCheckTrigger.class,
			localSyncService.syncCheckTriggerEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncRequestTimeoutEventProcessor(
		LocalSyncService localSyncService
	) {
		return new EventProcessorOnRunner<>(
			"sync",
			SyncRequestTimeout.class,
			localSyncService.syncRequestTimeoutEventProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncLedgerUpdateTimeoutEventProcessor(
		LocalSyncService localSyncService
	) {
		return new EventProcessorOnRunner<>(
			"sync",
			SyncLedgerUpdateTimeout.class,
			localSyncService.syncLedgerUpdateTimeoutProcessor()
		);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> syncCheckReceiveStatusTimeoutEventProcessor(
		LocalSyncService localSyncService
	) {
		return new EventProcessorOnRunner<>(
			"sync",
			SyncCheckReceiveStatusTimeout.class,
			localSyncService.syncCheckReceiveStatusTimeoutEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> statusResponseEventProcessor(
		LocalSyncService localSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			"sync",
			StatusResponse.class,
			localSyncService.statusResponseEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> syncResponseEventProcessor(
		LocalSyncService localSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			"sync",
			SyncResponse.class,
			localSyncService.syncResponseEventProcessor()
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessorOnRunner<?> ledgerStatusUpdateEventProcessor(
		LocalSyncService localSyncService
	) {
		return new RemoteEventProcessorOnRunner<>(
			"sync",
			LedgerStatusUpdate.class,
			localSyncService.ledgerStatusUpdateEventProcessor()
		);
	}
}
