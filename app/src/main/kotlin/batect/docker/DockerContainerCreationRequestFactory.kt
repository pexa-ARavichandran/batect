/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.docker

import batect.cli.CommandLineOptions
import batect.config.Container
import batect.primitives.mapToSet

class DockerContainerCreationRequestFactory(
    private val environmentVariableProvider: DockerContainerEnvironmentVariableProvider,
    private val resourceNameGenerator: DockerResourceNameGenerator,
    private val commandLineOptions: CommandLineOptions
) {
    fun create(
        container: Container,
        image: DockerImage,
        network: DockerNetwork,
        volumeMounts: Set<DockerVolumeMount>,
        userAndGroup: UserAndGroup?,
        terminalType: String?,
        useTTY: Boolean,
        attachStdin: Boolean
    ): ContainerCreationRequest {
        val portMappings = if (commandLineOptions.disablePortMappings)
            emptySet()
        else
            container.portMappings.mapToSet { it.toDockerPortMapping() }

        return ContainerCreationRequest(
            resourceNameGenerator.generateNameFor(container),
            image,
            network,
            if (container.command != null) container.command.parsedCommand else emptyList(),
            if (container.entrypoint != null) container.entrypoint.parsedCommand else emptyList(),
            container.name,
            container.additionalHostnames + container.name,
            container.additionalHosts,
            environmentVariableProvider.environmentVariablesFor(container, terminalType),
            container.workingDirectory,
            volumeMounts,
            container.deviceMounts.mapToSet { it.toDockerMount() },
            portMappings,
            container.healthCheckConfig.toDockerHealthCheckConfig(),
            userAndGroup,
            container.privileged,
            container.enableInitProcess,
            container.capabilitiesToAdd,
            container.capabilitiesToDrop,
            useTTY,
            attachStdin,
            container.logDriver,
            container.logOptions,
            container.shmSize?.bytes
        )
    }
}
