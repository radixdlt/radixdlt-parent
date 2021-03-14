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

package com.radixdlt.test.chaos;

import com.google.common.base.Joiner;
import com.radixdlt.test.Cluster;
import com.radixdlt.test.Conditions;
import com.radixdlt.test.LivenessCheck;
import com.radixdlt.test.RemoteBFTNetworkBridge;
import com.radixdlt.test.chaos.actions.Action;
import com.radixdlt.test.chaos.actions.NetworkAction;
import com.radixdlt.test.chaos.actions.RestartAction;
import com.radixdlt.test.chaos.actions.ValidatorRegistrationAction;
import com.radixdlt.test.chaos.actions.ShutdownAction;
import com.radixdlt.test.chaos.actions.MempoolFillAction;
import com.radixdlt.test.chaos.ansible.AnsibleImageWrapper;
import com.radixdlt.test.chaos.utils.ChaosExperimentUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Category(Cluster.class)
public class ChaosExperiments {

    private static final Logger logger = LogManager.getLogger();

    private final AnsibleImageWrapper ansible = AnsibleImageWrapper.createWithDefaultImage();

    @Test
    public void pre_release_experiment() {
        ChaosExperimentUtils.livenessCheckIgnoringOffline(ansible.toNetwork());

        Set<Action> actions = Set.of(
                new NetworkAction(ansible, 0.4),
                new RestartAction(ansible, 0.7),
                new ShutdownAction(ansible, 0.1),
                new MempoolFillAction(ansible, 0.8, 300),
                new ValidatorRegistrationAction(ansible, 0.2)
        );

        actions.forEach(Action::teardown);
        actions.forEach(Action::setup);

        ChaosExperimentUtils.livenessCheckIgnoringOffline(ansible.toNetwork());
    }

}
