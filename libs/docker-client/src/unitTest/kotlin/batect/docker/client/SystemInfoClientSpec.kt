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

import batect.docker.DockerException
import batect.docker.DockerVersionInfo
import batect.docker.api.BuilderVersion
import batect.docker.api.PingResponse
import batect.docker.api.SystemInfoAPI
import batect.primitives.Version
import batect.telemetry.AttributeValue
import batect.telemetry.CommonAttributes
import batect.telemetry.CommonEvents
import batect.telemetry.TelemetrySessionBuilder
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.IOException

object SystemInfoClientSpec : Spek({
    describe("a Docker system info client") {
        val api by createForEachTest { mock<SystemInfoAPI>() }
        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val client by createForEachTest { SystemInfoClient(api, telemetrySessionBuilder, logger) }

        describe("getting Docker version information") {
            on("the Docker version command invocation succeeding") {
                val versionInfo = DockerVersionInfo(Version(17, 4, 0), "1.27", "1.12", "deadbee", "my_cool_os", false)

                beforeEachTest { whenever(api.getServerVersionInfo()).doReturn(versionInfo) }

                it("returns the version information from Docker") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Succeeded(versionInfo)))
                }
            }

            on("running the Docker version command throwing an exception (for example, because Docker is not installed)") {
                beforeEachTest { whenever(api.getServerVersionInfo()).doThrow(RuntimeException("Something went wrong")) }

                it("returns an appropriate message") {
                    assertThat(client.getDockerVersionInfo(), equalTo(DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because RuntimeException was thrown: Something went wrong")))
                }
            }
        }

        describe("checking connectivity to the Docker daemon") {
            given("pinging the daemon succeeds") {
                beforeEachTest {
                    whenever(api.ping()).thenReturn(PingResponse(BuilderVersion.BuildKit))
                }

                given("getting daemon version info succeeds") {
                    given("the daemon is running on Linux") {
                        val operatingSystem = "linux"

                        given("the daemon reports an API version that is greater than required") {
                            beforeEachTest {
                                whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.38", "xxx", "xxx", operatingSystem, false))
                            }

                            it("returns success") {
                                assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded(DockerContainerType.Linux, Version(1, 2, 3), BuilderVersion.BuildKit, false)))
                            }
                        }

                        given("the daemon reports an API version that is exactly the required version") {
                            beforeEachTest {
                                whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.37", "xxx", "xxx", operatingSystem, false))
                            }

                            it("returns success") {
                                assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded(DockerContainerType.Linux, Version(1, 2, 3), BuilderVersion.BuildKit, false)))
                            }
                        }

                        given("the daemon reports an API version that is lower than required") {
                            beforeEachTest {
                                whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "1.36", "xxx", "xxx", operatingSystem, false))
                            }

                            val result by runForEachTest { client.checkConnectivity() }

                            it("returns failure") {
                                assertThat(result, equalTo(DockerConnectivityCheckResult.Failed("Batect requires Docker 18.03.1 or later, but version 1.2.3 is installed.")))
                            }

                            it("reports the version incompatibility in telemetry") {
                                verify(telemetrySessionBuilder).addEvent(
                                    "IncompatibleDockerVersion",
                                    mapOf(
                                        "dockerVersion" to AttributeValue("1.2.3")
                                    )
                                )
                            }
                        }
                    }

                    given("the daemon is running in Windows mode") {
                        given("the daemon reports a compatible API version") {
                            beforeEachTest {
                                whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "2.0", "xxx", "xxx", "windows", false))
                            }

                            it("returns success") {
                                assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Succeeded(DockerContainerType.Windows, Version(1, 2, 3), BuilderVersion.BuildKit, false)))
                            }
                        }
                    }

                    given("the daemon is running in another mode") {
                        beforeEachTest {
                            whenever(api.getServerVersionInfo()).thenReturn(DockerVersionInfo(Version(1, 2, 3), "2.0", "xxx", "xxx", "something-else", false))
                        }

                        it("returns success") {
                            assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Batect requires Docker to be running in Linux or Windows containers mode.")))
                        }
                    }
                }

                given("getting daemon version info fails") {
                    beforeEachTest {
                        whenever(api.getServerVersionInfo()).doThrow(DockerException("Something went wrong."))
                    }

                    it("returns failure") {
                        assertThat(client.checkConnectivity(), equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                    }
                }
            }

            given("pinging the daemon fails with a general Docker exception") {
                val exception = DockerException("Something went wrong.")

                beforeEachTest { whenever(api.ping()).doThrow(exception) }

                val result by runForEachTest { client.checkConnectivity() }

                it("returns failure") {
                    assertThat(result, equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                }

                it("reports the exception in telemetry") {
                    verify(telemetrySessionBuilder).addEvent(
                        CommonEvents.UnhandledException,
                        mapOf(
                            CommonAttributes.Exception to AttributeValue(exception),
                            CommonAttributes.ExceptionCaughtAt to AttributeValue("batect.docker.client.SystemInfoClient.checkConnectivity"),
                            CommonAttributes.IsUserFacingException to AttributeValue(true)
                        )
                    )
                }
            }

            given("pinging the daemon fails due to an I/O issue") {
                val exception = IOException("Something went wrong.")

                beforeEachTest {
                    whenever(api.ping()).doAnswer { throw exception }
                }

                val result by runForEachTest { client.checkConnectivity() }

                it("returns failure") {
                    assertThat(result, equalTo(DockerConnectivityCheckResult.Failed("Something went wrong.")))
                }

                it("reports the exception in telemetry") {
                    verify(telemetrySessionBuilder).addEvent(
                        CommonEvents.UnhandledException,
                        mapOf(
                            CommonAttributes.Exception to AttributeValue(exception),
                            CommonAttributes.ExceptionCaughtAt to AttributeValue("batect.docker.client.SystemInfoClient.checkConnectivity"),
                            CommonAttributes.IsUserFacingException to AttributeValue(true)
                        )
                    )
                }
            }
        }
    }
})
