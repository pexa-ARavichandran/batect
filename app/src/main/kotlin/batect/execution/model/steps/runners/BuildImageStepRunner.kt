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

import batect.cli.CommandLineOptions
import batect.config.BuildImage
import batect.config.Expression
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.config.TaskSpecialisedConfiguration
import batect.docker.ImageBuildFailedException
import batect.docker.api.BuilderVersion
import batect.docker.build.BuildProgress
import batect.docker.client.ImageBuildRequest
import batect.docker.client.ImagesClient
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.BuildImageStep
import batect.os.PathResolutionResult
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.proxies.ProxyEnvironmentVariablesProvider
import batect.telemetry.TelemetrySessionBuilder
import batect.ui.containerio.ContainerIOStreamingOptions
import java.nio.file.Path

class BuildImageStepRunner(
    private val config: TaskSpecialisedConfiguration,
    private val imagesClient: ImagesClient,
    private val proxyEnvironmentVariablesProvider: ProxyEnvironmentVariablesProvider,
    private val pathResolverFactory: PathResolverFactory,
    private val expressionEvaluationContext: ExpressionEvaluationContext,
    private val cancellationContext: CancellationContext,
    private val ioStreamingOptions: ContainerIOStreamingOptions,
    private val commandLineOptions: CommandLineOptions,
    private val builderVersion: BuilderVersion,
    private val systemInfo: SystemInfo,
    private val telemetrySessionBuilder: TelemetrySessionBuilder
) {
    fun run(step: BuildImageStep, eventSink: TaskEventSink) {
        try {
            val onStatusUpdate = { p: BuildProgress ->
                eventSink.postEvent(ImageBuildProgressEvent(step.container, p))
            }

            val buildConfig = step.container.imageSource as BuildImage
            val buildArgs = buildTimeProxyEnvironmentVariablesForOptions() + substituteBuildArgs(buildConfig.buildArgs)
            val imageTags = commandLineOptions.imageTags.getOrDefault(step.container.name, emptySet()) + imageTagFor(step)

            val request = ImageBuildRequest(
                resolveBuildDirectory(buildConfig),
                buildArgs,
                buildConfig.dockerfilePath,
                buildConfig.pathResolutionContext,
                imageTags,
                buildConfig.imagePullPolicy.forciblyPull,
                buildConfig.targetStage
            )

            val image = telemetrySessionBuilder.addSpan("BuildImage") {
                imagesClient.build(request, builderVersion, ioStreamingOptions.stdoutForImageBuild(step.container), cancellationContext, onStatusUpdate)
            }

            eventSink.postEvent(ImageBuiltEvent(step.container, image))
        } catch (e: ImageBuildFailedException) {
            val message = e.message ?: ""

            eventSink.postEvent(ImageBuildFailedEvent(step.container, message.replace("\n", systemInfo.lineSeparator)))
        }
    }

    private fun resolveBuildDirectory(source: BuildImage): Path {
        val pathResolver = pathResolverFactory.createResolver(source.pathResolutionContext)
        val evaluatedBuildDirectory = evaluateBuildDirectory(source.buildDirectory)

        when (val resolutionResult = pathResolver.resolve(evaluatedBuildDirectory)) {
            is PathResolutionResult.Resolved -> when (resolutionResult.pathType) {
                PathType.Directory -> return resolutionResult.absolutePath
                PathType.DoesNotExist -> throw ImageBuildFailedException("Build directory '${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) does not exist.")
                else -> throw ImageBuildFailedException("Build directory '${resolutionResult.originalPath}' (${resolutionResult.resolutionDescription}) is not a directory.")
            }
            is PathResolutionResult.InvalidPath -> throw ImageBuildFailedException("Build directory '${resolutionResult.originalPath}' is not a valid path.")
        }
    }

    private fun substituteBuildArgs(original: Map<String, Expression>): Map<String, String> =
        original.mapValues { (name, value) -> evaluateBuildArgValue(name, value) }

    private fun evaluateBuildArgValue(name: String, expression: Expression): String {
        try {
            return expression.evaluate(expressionEvaluationContext)
        } catch (e: ExpressionEvaluationException) {
            throw ImageBuildFailedException("The value for the build arg '$name' cannot be evaluated: ${e.message}", e)
        }
    }

    private fun evaluateBuildDirectory(expression: Expression): String {
        try {
            return expression.evaluate(expressionEvaluationContext)
        } catch (e: ExpressionEvaluationException) {
            throw ImageBuildFailedException("The value for the build directory cannot be evaluated: ${e.message}", e)
        }
    }

    private fun buildTimeProxyEnvironmentVariablesForOptions(): Map<String, String> = if (commandLineOptions.dontPropagateProxyEnvironmentVariables) {
        emptyMap()
    } else {
        proxyEnvironmentVariablesProvider.getProxyEnvironmentVariables(emptySet())
    }

    private fun imageTagFor(step: BuildImageStep): String = "${config.projectName}-${step.container.name}"
}
