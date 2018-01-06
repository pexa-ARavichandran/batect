/*
   Copyright 2017 Charles Korn.

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

import batect.config.Container
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.os.ProxyEnvironmentVariablesProvider
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.isEmptyMap
import batect.testutils.withMessage
import batect.ui.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DockerContainerCreationRequestFactorySpec : Spek({
    describe("a Docker container creation request factory") {
        val image = DockerImage("some-image")
        val network = DockerNetwork("some-network")
        val command = listOf("some-app", "some-arg")

        given("the console's type is not available") {
            val consoleInfo = mock<ConsoleInfo>()
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider>()
            val hostEnvironmentVariables = emptyMap<String, String>()
            val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)
            val propagateProxyEnvironmentVariables = false

            given("there are no additional environment variables") {
                val additionalEnvironmentVariables = emptyMap<String, String>()

                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    command = "some-command-that-wont-be-used",
                    workingDirectory = "/some-work-dir",
                    volumeMounts = setOf(VolumeMount("local", "remote", "mode")),
                    portMappings = setOf(PortMapping(123, 456)),
                    environment = mapOf("SOME_VAR" to "SOME_VALUE"),
                    healthCheckConfig = HealthCheckConfig("2s", 10, "5s")
                )

                on("creating the request") {
                    val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                    it("populates the image on the request") {
                        assertThat(request.image, equalTo(image))
                    }

                    it("populates the network on the request") {
                        assertThat(request.network, equalTo(network))
                    }

                    it("populates the command on the request") {
                        assertThat(request.command, equalTo<Iterable<String>>(command))
                    }

                    it("populates the hostname and network alias on the request with the name of the container") {
                        assertThat(request.hostname, equalTo(container.name))
                        assertThat(request.networkAlias, equalTo(container.name))
                    }

                    it("populates the environment variables on the request with the environment variables from the container") {
                        assertThat(request.environmentVariables, equalTo(container.environment))
                    }

                    it("populates the working directory on the request with the working directory from the container") {
                        assertThat(request.workingDirectory, equalTo(container.workingDirectory))
                    }

                    it("populates the volume mounts on the request with the volume mounts from the container") {
                        assertThat(request.volumeMounts, equalTo(container.volumeMounts))
                    }

                    it("populates the port mappings on the request with the port mappings from the container") {
                        assertThat(request.portMappings, equalTo(container.portMappings))
                    }

                    it("populates the health check configuration on the request with the health check configuration from the container") {
                        assertThat(request.healthCheckConfig, equalTo(container.healthCheckConfig))
                    }
                }
            }

            given("there are additional environment variables") {
                val additionalEnvironmentVariables = mapOf(
                    "SOME_HOST_VAR" to "SOME_HOST_VALUE"
                )

                given("none of them conflict with environment variables on the container") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_VAR" to "SOME_VALUE")
                    )

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the environment variables from the container and from the additional environment variables") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_VAR" to "SOME_VALUE",
                                "SOME_HOST_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }

                given("one of them conflicts with environment variables on the container") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_HOST_VAR" to "SOME_CONTAINER_VALUE")
                    )

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the environment variables from the container and from the additional environment variables, with the additional environment variables taking precedence") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_HOST_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }
            }
        }

        given("there are references to host environment variables") {
            val consoleInfo = mock<ConsoleInfo>()
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider>()
            val hostEnvironmentVariables = mapOf("SOME_HOST_VARIABLE" to "SOME_HOST_VALUE")
            val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)
            val propagateProxyEnvironmentVariables = false

            given("and those references are on the container") {
                val additionalEnvironmentVariables = emptyMap<String, String>()

                given("and the reference is valid") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE")
                    )

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the environment variables' values from the host") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }

                given("and the reference is to an environment variable that does not exist on the host") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE_THAT_ISNT_DEFINED")
                    )

                    on("creating the request") {
                        it("throws an appropriate exception") {
                            assertThat({ factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables) },
                                throws<ContainerCreationFailedException>(withMessage("The environment variable 'SOME_VAR' refers to host environment variable 'SOME_HOST_VARIABLE_THAT_ISNT_DEFINED', but it is not set.")))
                        }
                    }
                }
            }

            given("and those references are in the additional environment variables") {
                given("and the reference is valid") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE")

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the environment variables' values from the host") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }

                given("and the references is to an environment variable that does not exist on the host") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE_THAT_ISNT_DEFINED")

                    on("creating the request") {
                        it("throws an appropriate exception") {
                            assertThat({ factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables) },
                                throws<ContainerCreationFailedException>(withMessage("The environment variable 'SOME_VAR' refers to host environment variable 'SOME_HOST_VARIABLE_THAT_ISNT_DEFINED', but it is not set.")))
                        }
                    }
                }

                given("and the reference overrides a container-level environment variable that does not exist on the host") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE_THAT_ISNT_DEFINED")
                    )

                    val additionalEnvironmentVariables = mapOf("SOME_VAR" to "\$SOME_HOST_VARIABLE")

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the environment variables' values from the host and does not throw an exception") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "SOME_VAR" to "SOME_HOST_VALUE"
                            )))
                        }
                    }
                }
            }
        }

        given("there are proxy environment variables present on the host") {
            val consoleInfo = mock<ConsoleInfo>()
            val hostEnvironmentVariables = emptyMap<String, String>()
            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
                on { proxyEnvironmentVariables } doReturn mapOf(
                    "HTTP_PROXY" to "http://some-proxy",
                    "NO_PROXY" to "dont-proxy-this"
                )
            }

            val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)

            given("propagating proxy environment variables is enabled") {
                val propagateProxyEnvironmentVariables = true

                given("neither the container nor the additional environment variables override the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = emptyMap<String, String>()

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the proxy environment variables from the host") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "HTTP_PROXY" to "http://some-proxy",
                                "NO_PROXY" to "dont-proxy-this"
                            )))
                        }
                    }
                }

                given("the container overrides the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf(
                            "HTTP_PROXY" to "http://some-other-proxy"
                        )
                    )

                    val additionalEnvironmentVariables = emptyMap<String, String>()

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the proxy environment variables from the host, with overrides from the container") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "HTTP_PROXY" to "http://some-other-proxy",
                                "NO_PROXY" to "dont-proxy-this"
                            )))
                        }
                    }
                }

                given("the additional environment variables override the proxy settings") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter(),
                        environment = mapOf(
                            "HTTP_PROXY" to "http://some-other-proxy"
                        )
                    )

                    val additionalEnvironmentVariables = mapOf(
                        "HTTP_PROXY" to "http://some-additional-proxy"
                    )

                    on("creating the request") {
                        val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                        it("populates the environment variables on the request with the proxy environment variables from the host, with overrides from the container and additional environment variables") {
                            assertThat(request.environmentVariables, equalTo(mapOf(
                                "HTTP_PROXY" to "http://some-additional-proxy",
                                "NO_PROXY" to "dont-proxy-this"
                            )))
                        }
                    }
                }
            }

            given("propagating proxy environment variables is disabled") {
                val propagateProxyEnvironmentVariables = false

                on("creating the request") {
                    val container = Container(
                        "some-container",
                        imageSourceDoesNotMatter()
                    )

                    val additionalEnvironmentVariables = emptyMap<String, String>()
                    val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                    it("does not propagate the proxy environment variables") {
                        assertThat(request.environmentVariables, isEmptyMap())
                    }
                }
            }
        }

        given("the console's type is available") {
            val consoleInfo = mock<ConsoleInfo> {
                on { terminalType } doReturn "some-term"
            }

            val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider>()
            val hostEnvironmentVariables = emptyMap<String, String>()
            val factory = DockerContainerCreationRequestFactory(consoleInfo, proxyEnvironmentVariablesProvider, hostEnvironmentVariables)
            val propagateProxyEnvironmentVariables = false

            given("a container with no override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf("SOME_VAR" to "SOME_VALUE")
                )

                val additionalEnvironmentVariables = emptyMap<String, String>()

                on("creating the request") {
                    val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the host") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-term"
                        )))
                    }
                }
            }

            given("a container with an override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf(
                        "SOME_VAR" to "SOME_VALUE",
                        "TERM" to "some-other-term"
                    )
                )

                val additionalEnvironmentVariables = emptyMap<String, String>()

                on("creating the request") {
                    val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the container") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-other-term"
                        )))
                    }
                }
            }

            given("some additional environment variables with an override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf(
                        "SOME_VAR" to "SOME_VALUE"
                    )
                )

                val additionalEnvironmentVariables = mapOf("TERM" to "some-additional-term")

                on("creating the request") {
                    val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the additional environment variables") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-additional-term"
                        )))
                    }
                }
            }

            given("both the container and the additional environment variables have an override for the TERM environment variable") {
                val container = Container(
                    "some-container",
                    imageSourceDoesNotMatter(),
                    environment = mapOf(
                        "SOME_VAR" to "SOME_VALUE",
                        "TERM" to "some-container-term"
                    )
                )

                val additionalEnvironmentVariables = mapOf("TERM" to "some-additional-term")

                on("creating the request") {
                    val request = factory.create(container, image, network, command, additionalEnvironmentVariables, propagateProxyEnvironmentVariables)

                    it("populates the environment variables on the request with the environment variables from the container and the TERM environment variable from the additional environment variables") {
                        assertThat(request.environmentVariables, equalTo(mapOf(
                            "SOME_VAR" to "SOME_VALUE",
                            "TERM" to "some-additional-term"
                        )))
                    }
                }
            }
        }
    }
})
