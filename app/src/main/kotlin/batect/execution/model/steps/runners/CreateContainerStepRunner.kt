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

package batect.execution.model.steps.runners

import batect.docker.ContainerCreationFailedException
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.client.ContainersClient
import batect.execution.RunAsCurrentUserConfigurationException
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.VolumeMountResolutionException
import batect.execution.VolumeMountResolver
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.CreateContainerStep
import batect.ui.containerio.ContainerIOStreamingOptions

class CreateContainerStepRunner(
    private val containersClient: ContainersClient,
    private val volumeMountResolver: VolumeMountResolver,
    private val runAsCurrentUserConfigurationProvider: RunAsCurrentUserConfigurationProvider,
    private val creationRequestFactory: DockerContainerCreationRequestFactory,
    private val ioStreamingOptions: ContainerIOStreamingOptions
) {
    fun run(step: CreateContainerStep, eventSink: TaskEventSink) {
        val container = step.container

        try {
            val resolvedMounts = volumeMountResolver.resolve(container.volumeMounts)
            val userAndGroup = runAsCurrentUserConfigurationProvider.determineUserAndGroup(container)

            runAsCurrentUserConfigurationProvider.createMissingVolumeMountDirectories(resolvedMounts, container)

            val creationRequest = creationRequestFactory.create(
                container,
                step.image,
                step.network,
                resolvedMounts,
                userAndGroup,
                ioStreamingOptions.terminalTypeForContainer(container),
                ioStreamingOptions.useTTYForContainer(container),
                ioStreamingOptions.attachStdinForContainer(container)
            )

            val dockerContainer = containersClient.create(creationRequest)

            try {
                runAsCurrentUserConfigurationProvider.applyConfigurationToContainer(container, dockerContainer)
            } finally {
                // We always want to post that we created the container, even if we didn't finish configuring it -
                // this ensures that we will clean it up correctly.
                eventSink.postEvent(ContainerCreatedEvent(container, dockerContainer))
            }
        } catch (e: ContainerCreationFailedException) {
            eventSink.postEvent(ContainerCreationFailedEvent(container, e.message ?: ""))
        } catch (e: VolumeMountResolutionException) {
            eventSink.postEvent(ContainerCreationFailedEvent(container, e.message ?: ""))
        } catch (e: RunAsCurrentUserConfigurationException) {
            eventSink.postEvent(ContainerCreationFailedEvent(container, e.message ?: ""))
        }
    }
}
