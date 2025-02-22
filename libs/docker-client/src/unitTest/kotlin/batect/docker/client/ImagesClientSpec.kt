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

package batect.docker.client

import batect.docker.DockerImage
import batect.docker.DockerRegistryCredentialsException
import batect.docker.DownloadOperation
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.ImageReference
import batect.docker.Json
import batect.docker.api.BuilderVersion
import batect.docker.api.ImagesAPI
import batect.docker.api.SessionStreams
import batect.docker.api.SessionsAPI
import batect.docker.build.BuildKitConfig
import batect.docker.build.BuildProgress
import batect.docker.build.LegacyBuilderConfig
import batect.docker.build.buildkit.BuildKitSession
import batect.docker.build.buildkit.BuildKitSessionFactory
import batect.docker.build.legacy.DockerfileParser
import batect.docker.build.legacy.ImageBuildContext
import batect.docker.build.legacy.ImageBuildContextFactory
import batect.docker.pull.ImagePullProgress
import batect.docker.pull.ImagePullProgressReporter
import batect.docker.pull.RegistryCredentials
import batect.docker.pull.RegistryCredentialsProvider
import batect.os.PathResolutionContext
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withCause
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okio.Sink
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object ImagesClientSpec : Spek({
    describe("a Docker images client") {
        val imagesAPI by createForEachTest { mock<ImagesAPI>() }
        val sessionsAPI by createForEachTest { mock<SessionsAPI>() }
        val credentialsProvider by createForEachTest { mock<RegistryCredentialsProvider>() }
        val imageBuildContextFactory by createForEachTest { mock<ImageBuildContextFactory>() }
        val dockerfileParser by createForEachTest { mock<DockerfileParser>() }
        val buildKitSessionFactory by createForEachTest { mock<BuildKitSessionFactory>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val imageProgressReporter by createForEachTest { mock<ImagePullProgressReporter>() }
        val imageProgressReporterFactory = { imageProgressReporter }
        val client by createForEachTest { ImagesClient(imagesAPI, sessionsAPI, credentialsProvider, imageBuildContextFactory, dockerfileParser, buildKitSessionFactory, logger, imageProgressReporterFactory) }

        describe("building an image") {
            val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
            val buildDirectory by createForEachTest { fileSystem.getPath("/path/to/build/dir") }
            val buildArgs = mapOf(
                "some_name" to "some_value",
                "some_other_name" to "some_other_value"
            )

            val dockerfilePath = "some-Dockerfile-path"
            val imageTags = setOf("some_image_tag", "some_other_image_tag")
            val forcePull = true
            val targetStage = "some-target-stage"

            val pathResolutionContext by createForEachTest {
                mock<PathResolutionContext> {
                    on { getPathForDisplay(buildDirectory) } doReturn "<a nicely formatted version of the build directory>"
                }
            }

            val outputSink by createForEachTest { mock<Sink>() }
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            given("the Dockerfile exists") {
                val resolvedDockerfilePath by createForEachTest { buildDirectory.resolve(dockerfilePath) }

                beforeEachTest {
                    Files.createDirectories(buildDirectory)
                    Files.createFile(resolvedDockerfilePath)

                    whenever(dockerfileParser.extractBaseImageNames(resolvedDockerfilePath)).doReturn(setOf(ImageReference("nginx:1.13.0"), ImageReference("some-other-image:2.3.4")))
                }

                given("getting the credentials for the base image succeeds") {
                    val image1Credentials = mock<RegistryCredentials>()
                    val image2Credentials = mock<RegistryCredentials>()

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials(ImageReference("nginx:1.13.0"))).doReturn(image1Credentials)
                        whenever(credentialsProvider.getCredentials(ImageReference("some-other-image:2.3.4"))).doReturn(image2Credentials)
                    }

                    val image = DockerImage("some-image-id")
                    beforeEachTest { whenever(imagesAPI.build(any(), any(), any(), any(), anyOrNull(), any(), any(), any(), any())).doReturn(image) }

                    val onStatusUpdate = fun(_: BuildProgress) {}

                    given("the legacy builder is being used") {
                        val context = ImageBuildContext(emptySet())

                        beforeEachTest {
                            whenever(imageBuildContextFactory.createFromDirectory(buildDirectory, dockerfilePath)).doReturn(context)
                        }

                        val request by createForEachTest {
                            ImageBuildRequest(
                                buildDirectory,
                                buildArgs,
                                dockerfilePath,
                                pathResolutionContext,
                                imageTags,
                                forcePull,
                                targetStage
                            )
                        }

                        val result by runForEachTest { client.build(request, BuilderVersion.Legacy, outputSink, cancellationContext, onStatusUpdate) }

                        it("builds the image") {
                            verify(imagesAPI).build(
                                eq(buildArgs),
                                eq(dockerfilePath),
                                eq(imageTags),
                                eq(forcePull),
                                eq(targetStage),
                                argThat { destinationSink == outputSink },
                                eq(LegacyBuilderConfig(setOf(image1Credentials, image2Credentials), context)),
                                eq(cancellationContext),
                                eq(onStatusUpdate)
                            )
                        }

                        it("does not start a session") {
                            verify(sessionsAPI, never()).create(any())
                        }

                        it("returns the built image") {
                            assertThat(result, equalTo(image))
                        }
                    }

                    given("BuildKit is being used") {
                        val buildKitSession by createForEachTest { mock<BuildKitSession>() }
                        beforeEachTest { whenever(buildKitSessionFactory.create(eq(buildDirectory), argThat { destinationSink == outputSink })).thenReturn(buildKitSession) }

                        val sessionStreams by createForEachTest { mock<SessionStreams>() }
                        beforeEachTest { whenever(sessionsAPI.create(buildKitSession)).thenReturn(sessionStreams) }

                        val request by createForEachTest {
                            ImageBuildRequest(
                                buildDirectory,
                                buildArgs,
                                dockerfilePath,
                                pathResolutionContext,
                                imageTags,
                                forcePull,
                                targetStage
                            )
                        }

                        val result by runForEachTest { client.build(request, BuilderVersion.BuildKit, outputSink, cancellationContext, onStatusUpdate) }

                        it("builds the image") {
                            verify(imagesAPI).build(
                                eq(buildArgs),
                                eq(dockerfilePath),
                                eq(imageTags),
                                eq(forcePull),
                                eq(targetStage),
                                argThat { destinationSink == outputSink },
                                eq(BuildKitConfig(buildKitSession)),
                                eq(cancellationContext),
                                eq(onStatusUpdate)
                            )
                        }

                        it("creates the session") {
                            verify(sessionsAPI).create(buildKitSession)
                        }

                        it("starts the session before building the image, then closes the session") {
                            inOrder(sessionsAPI, imagesAPI, buildKitSession) {
                                verify(sessionsAPI).create(any())
                                verify(buildKitSession).start(sessionStreams)
                                verify(imagesAPI).build(any(), any(), any(), any(), anyOrNull(), any(), any(), any(), any())
                                verify(buildKitSession).close()
                            }
                        }

                        it("returns the built image") {
                            assertThat(result, equalTo(image))
                        }
                    }
                }

                given("getting credentials for the base image fails") {
                    val request by createForEachTest {
                        ImageBuildRequest(
                            buildDirectory,
                            buildArgs,
                            dockerfilePath,
                            pathResolutionContext,
                            imageTags,
                            forcePull
                        )
                    }

                    val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials(ImageReference("nginx:1.13.0"))).thenThrow(exception)
                    }

                    on("building the image") {
                        it("throws an appropriate exception") {
                            assertThat(
                                { client.build(request, BuilderVersion.Legacy, outputSink, cancellationContext) {} },
                                throws<ImageBuildFailedException>(
                                    withMessage("Could not build image: Could not load credentials: something went wrong.")
                                        and withCause(exception)
                                )
                            )
                        }
                    }
                }
            }

            given("the Dockerfile does not exist") {
                val request by createForEachTest {
                    ImageBuildRequest(
                        buildDirectory,
                        buildArgs,
                        dockerfilePath,
                        pathResolutionContext,
                        imageTags,
                        forcePull
                    )
                }

                on("building the image") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { client.build(request, BuilderVersion.Legacy, outputSink, cancellationContext) {} },
                            throws<ImageBuildFailedException>(withMessage("Could not build image: the Dockerfile 'some-Dockerfile-path' does not exist in the build directory <a nicely formatted version of the build directory>"))
                        )
                    }
                }
            }

            given("the Dockerfile exists but is not a child of the build directory") {
                val dockerfilePathOutsideBuildDir = "../some-Dockerfile"
                val resolvedDockerfilePath by createForEachTest { buildDirectory.resolve(dockerfilePathOutsideBuildDir) }

                val request by createForEachTest {
                    ImageBuildRequest(
                        buildDirectory,
                        buildArgs,
                        dockerfilePathOutsideBuildDir,
                        pathResolutionContext,
                        imageTags,
                        forcePull
                    )
                }

                beforeEachTest {
                    Files.createDirectories(buildDirectory)
                    Files.createFile(resolvedDockerfilePath)
                }

                on("building the image") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { client.build(request, BuilderVersion.Legacy, outputSink, cancellationContext) {} },
                            throws<ImageBuildFailedException>(withMessage("Could not build image: the Dockerfile '../some-Dockerfile' is not a child of the build directory <a nicely formatted version of the build directory>"))
                        )
                    }
                }
            }
        }

        describe("pulling an image") {
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            given("forcibly pulling the image is disabled") {
                val forcePull = false

                given("the image does not exist locally") {
                    beforeEachTest {
                        whenever(imagesAPI.hasImage(ImageReference("some-image"))).thenReturn(false)
                    }

                    given("getting credentials for the image succeeds") {
                        val credentials = mock<RegistryCredentials>()

                        beforeEachTest {
                            whenever(credentialsProvider.getCredentials(ImageReference("some-image"))).thenReturn(credentials)
                        }

                        on("pulling the image") {
                            val firstProgressUpdate = Json.default.parseToJsonElement("""{"thing": "value"}""").jsonObject
                            val secondProgressUpdate = Json.default.parseToJsonElement("""{"thing": "other value"}""").jsonObject

                            beforeEachTest {
                                whenever(imageProgressReporter.processProgressUpdate(firstProgressUpdate)).thenReturn(ImagePullProgress(DownloadOperation.Downloading, 10, 20))
                                whenever(imageProgressReporter.processProgressUpdate(secondProgressUpdate)).thenReturn(null)

                                whenever(imagesAPI.pull(any(), any(), any(), any())).then { invocation ->
                                    @Suppress("UNCHECKED_CAST")
                                    val onProgressUpdate = invocation.arguments[3] as (JsonObject) -> Unit
                                    onProgressUpdate(firstProgressUpdate)
                                    onProgressUpdate(secondProgressUpdate)

                                    null
                                }
                            }

                            val progressUpdatesReceived by createForEachTest { mutableListOf<ImagePullProgress>() }
                            val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext) { progressUpdatesReceived.add(it) } }

                            it("calls the Docker API to pull the image") {
                                verify(imagesAPI).pull(eq(ImageReference("some-image")), eq(credentials), eq(cancellationContext), any())
                            }

                            it("sends notifications for all relevant progress updates") {
                                assertThat(progressUpdatesReceived, equalTo(listOf(ImagePullProgress(DownloadOperation.Downloading, 10, 20))))
                            }

                            it("returns the Docker image") {
                                assertThat(image, equalTo(DockerImage("some-image")))
                            }
                        }
                    }

                    given("getting credentials for the image fails") {
                        val exception = DockerRegistryCredentialsException("Could not load credentials: something went wrong.")

                        beforeEachTest {
                            whenever(credentialsProvider.getCredentials(ImageReference("some-image"))).thenThrow(exception)
                        }

                        on("pulling the image") {
                            it("throws an appropriate exception") {
                                assertThat(
                                    { client.pull("some-image", forcePull, cancellationContext, {}) },
                                    throws<ImagePullFailedException>(
                                        withMessage("Could not pull image 'some-image': Could not load credentials: something went wrong.")
                                            and withCause(exception)
                                    )
                                )
                            }
                        }
                    }
                }

                on("when the image already exists locally") {
                    beforeEachTest { whenever(imagesAPI.hasImage(ImageReference("some-image"))).thenReturn(true) }

                    val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext, {}) }

                    it("does not call the Docker API to pull the image again") {
                        verify(imagesAPI, never()).pull(any(), any(), any(), any())
                    }

                    it("returns the Docker image") {
                        assertThat(image, equalTo(DockerImage("some-image")))
                    }
                }
            }

            given("forcibly pulling the image is enabled") {
                val forcePull = true

                given("getting credentials for the image succeeds") {
                    val credentials = mock<RegistryCredentials>()

                    beforeEachTest {
                        whenever(credentialsProvider.getCredentials(ImageReference("some-image"))).thenReturn(credentials)
                    }

                    on("pulling the image") {
                        val image by runForEachTest { client.pull("some-image", forcePull, cancellationContext, {}) }

                        it("calls the Docker API to pull the image") {
                            verify(imagesAPI).pull(eq(ImageReference("some-image")), eq(credentials), eq(cancellationContext), any())
                        }

                        it("returns the Docker image") {
                            assertThat(image, equalTo(DockerImage("some-image")))
                        }

                        it("does not call the Docker API to check if the image has already been pulled") {
                            verify(imagesAPI, never()).hasImage(any())
                        }
                    }
                }
            }
        }
    }
})
