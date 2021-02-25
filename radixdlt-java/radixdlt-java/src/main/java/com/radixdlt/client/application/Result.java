/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.application;

import com.radixdlt.client.application.translate.ActionExecutionException;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observables.ConnectableObservable;

public class Result {
	private final ConnectableObservable<SubmitAtomAction> updates;
	private final Completable completable;
	private final Single<Atom> cachedAtom;

	Result(
		ConnectableObservable<SubmitAtomAction> updates,
		Single<Atom> cachedAtom,
		List<AtomErrorToExceptionReasonMapper> errorMappers
	) {
		this.updates = updates;
		this.cachedAtom = cachedAtom;
		this.completable = updates
			.ofType(SubmitAtomStatusAction.class)
			.lastOrError()
			.flatMapCompletable(status -> toCompletable(errorMappers, status));
	}

	private Completable toCompletable(List<AtomErrorToExceptionReasonMapper> atomErrorMappers, SubmitAtomStatusAction status) {
		if (status.getStatusNotification().getAtomStatus() == AtomStatus.STORED) {
			return Completable.complete();
		} else {
			return Completable.error(ActionExecutionException.create(atomErrorMappers, status));
		}
	}

	Result connect() {
		updates.connect();
		return this;
	}

	/**
	 * Get the atom which was sent for submission
	 *
	 * @return the atom which was sent
	 */
	public Atom getAtom() {
		return cachedAtom.blockingGet();
	}

	/**
	 * A low level interface, returns an a observable of the status of an atom submission as it occurs.
	 *
	 * @return observable of atom submission status
	 */
	public Observable<SubmitAtomAction> toObservable() {
		return updates;
	}

	/**
	 * A high level interface, returns completable of successful completion of action execution.
	 * If there is an with the ledger, the completable throws an ActionExecutionException.
	 *
	 * @return completable of successful execution of action onto ledger.
	 */
	public Completable toCompletable() {
		return completable;
	}

	/**
	 * Block until the execution of the action is stored on the node ledger.
	 * Throws an exception if there are any issues.
	 */
	public void blockUntilComplete() {
		completable.blockingAwait();
	}
}
