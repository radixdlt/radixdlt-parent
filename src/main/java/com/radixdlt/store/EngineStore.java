package com.radixdlt.store;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.engine.CMAtom;
import java.util.function.Consumer;

/**
 *  A state that gives access to the state of a certain shard space
 */
public interface EngineStore extends CMStore {
	/**
	 * Retrieves the atom containing the given spun particle.
	 * TODO: change to reactive streams interface
	 */
	void getAtomContaining(SpunParticle spunParticle, Consumer<ImmutableAtom> callback);

	/**
	 * Stores the atom into this CMStore
	 */
	void storeAtom(CMAtom atom, Object computed);

	/**
	 * Deletes an atom and all it's dependencies
	 */
	void deleteAtom(CMAtom atom);
}
