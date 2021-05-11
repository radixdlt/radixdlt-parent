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

package utils

class SlowNodeSetup {
    String image, keyVolume, clusterName
    int numOfSlowNodes
    private String dockerRunOptions
    private String addtionalDockerCmdOptions
    private String sshDestinationLocDir = "/ansible/ssh"
    private String sshDestinationFileName = "testnet"
    private String dockerBinary = Optional.ofNullable(System.getenv("DOCKER_BINARY")).orElse("docker")


    private SlowNodeSetup(String image, String runOptions, String cmdOptions, int numOfSlowNodes, String clusterName) {
        this.image = image
        this.dockerRunOptions = runOptions
        this.addtionalDockerCmdOptions = cmdOptions
        this.numOfSlowNodes = numOfSlowNodes
        this.clusterName = clusterName
    }

    void copyfileToNamedVolume(String fileLocation, String keyVolume) {
        CmdHelper.runCommand("${dockerBinary} container create --name dummy -v ${keyVolume}:${sshDestinationLocDir} curlimages/curl:7.70.0")
        CmdHelper.runCommand("${dockerBinary} cp ${fileLocation} dummy:${sshDestinationLocDir}/${sshDestinationFileName}")
        CmdHelper.runCommand("${dockerBinary} rm -f dummy")
    }

    void pullImage() {
        CmdHelper.runCommand("${dockerBinary} pull ${image}")
    }

    void setAddtionalDockerCmdOptions(cmdOptions){
        this.addtionalDockerCmdOptions = cmdOptions
    }

    void setup() {
        (1..numOfSlowNodes).each {
            def runnerCommand = "/bin/bash -c".tokenize() << (
                    "${dockerBinary} run " +
                            "${dockerRunOptions ?: ''} " +
                            "${this.image} " +
                            "slow-down-node.yml " +
                            "${addtionalDockerCmdOptions ?: ''} " +
                            "--limit ${clusterName}[${it - 1}] -t setup ")
            CmdHelper.runCommand(runnerCommand,Generic.getAWSCredentials()as String[])
        }
    }

    void tearDown() {
        (1..numOfSlowNodes).each {
            def runnerCommand = "/bin/bash -c".tokenize() << (
                    "${dockerBinary} run " +
                            "${dockerRunOptions ?: ''} " +
                            "${this.image} " +
                            "slow-down-node.yml " +
                            "${addtionalDockerCmdOptions ?: ''} " +
                            "--limit ${clusterName}[${it - 1}] -t teardown ")
            CmdHelper.runCommand(runnerCommand,Generic.getAWSCredentials()as String[])
        }
        CmdHelper.runCommand("${dockerBinary} volume rm -f ${keyVolume}")

    }

    static Builder builder() {
        return new Builder()
    }

    /**
     * Will use ansible to run a task blocking the given port number, across all nodes of this network
     */
    void togglePortViaAnsible(int portNumber, boolean shouldEnable) {
        def extraVariables = (shouldEnable) ? "-e 'port_number=${portNumber}'" : ""
        def tag = (shouldEnable) ? "-t block-port-container" : "-t restore-blocked-port-container"

        def dockerCommand = "${dockerBinary} run " +
                        "${dockerRunOptions ?: ''} " +
                        "${this.image} " +
                        "system-testing.yml " +
                        "${addtionalDockerCmdOptions ?: ''} " +
                        "--limit ${clusterName} ${tag} ${extraVariables}"
        CmdHelper.runCommand(dockerCommand,Generic.getAWSCredentials()as String[])
    }

    static class Builder {
        String image, runOptions, cmdOptions, clusterName
        int numOfSlowNodes

        private Builder() {
        }

        Builder withImage(String image) {
            String imageTag = Optional.ofNullable(System.getenv("ANSIBLE_TAG")).orElse("latest")
            this.image = "${image}:${imageTag}"
            return this
        }

        Builder nodesToSlowDown(int numOfSlowNodes) {
            this.numOfSlowNodes = numOfSlowNodes
            return this
        }

        Builder runOptions(String runOptions) {
            this.runOptions = runOptions
            return this
        }

        Builder cmdOptions(String cmdOptions) {
            this.cmdOptions = cmdOptions
            return this
        }

        Builder usingCluster(String clusterName) {
            this.clusterName = clusterName
            return this
        }

        SlowNodeSetup build() {
            Objects.requireNonNull(this.image)
            Objects.requireNonNull(this.clusterName)
            Objects.requireNonNull(this.numOfSlowNodes)
            return new SlowNodeSetup(this.image, this.runOptions, this.cmdOptions, this.numOfSlowNodes, this.clusterName)
        }
    }


}

