/*
   Copyright 2017-2018 Charles Korn.

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

package batect.ui.fancy

import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.testutils.createForEachTest
import batect.testutils.equivalentTo
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.text.Text
import com.natpryce.hamkrest.assertion.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CleanupProgressDisplayLineSpec : Spek({
    describe("a cleanup progress display line") {
        val cleanupDisplay by createForEachTest { CleanupProgressDisplayLine() }

        describe("printing cleanup progress to the console") {
            on("when there is nothing to clean up") {
                val output = cleanupDisplay.print()

                it("prints that clean up is complete") {
                    assertThat(output, equivalentTo(Text.white("Clean up: done")))
                }
            }

            describe("when there is only the network to clean up") {
                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                }

                on("and the network hasn't been removed yet") {
                    val output = cleanupDisplay.print()

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and the network has been removed") {
                    cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)

                    val output = cleanupDisplay.print()

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there is a container and the network to clean up") {
                val container = Container("some-container", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container, DockerContainer("some-container-id")))
                }

                on("and the container hasn't been removed yet") {
                    val output = cleanupDisplay.print()

                    it("prints that the container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("some-container") + Text(") left to remove..."))))
                    }
                }

                on("and the container has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))

                    val output = cleanupDisplay.print()

                    it("prints that the network still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white("Cleaning up: removing task network...")))
                    }
                }

                on("and the network has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container))
                    cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)

                    val output = cleanupDisplay.print()

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there are two containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                }

                on("and neither container has been removed yet") {
                    val output = cleanupDisplay.print()

                    it("prints that both of the containers still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 2 containers (") + Text.bold("container-1") + Text(" and ") + Text.bold("container-2") + Text(") left to remove..."))))
                    }
                }

                on("and one container has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container1))

                    val output = cleanupDisplay.print()

                    it("prints that the other container still needs to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 1 container (") + Text.bold("container-2") + Text(") left to remove..."))))
                    }
                }

                on("and the network has been removed") {
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container1))
                    cleanupDisplay.onEventPosted(ContainerRemovedEvent(container2))
                    cleanupDisplay.onEventPosted(TaskNetworkDeletedEvent)

                    val output = cleanupDisplay.print()

                    it("prints that clean up is complete") {
                        assertThat(output, equivalentTo(Text.white("Clean up: done")))
                    }
                }
            }

            describe("when there are three containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())
                val container3 = Container("container-3", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container3, DockerContainer("container-3-id")))
                }

                on("and none of the containers have been removed yet") {
                    val output = cleanupDisplay.print()

                    it("prints that all of the containers still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 3 containers (") + Text.bold("container-1") + Text(", ") + Text.bold("container-2") + Text(" and ") + Text.bold("container-3") + Text(") left to remove..."))))
                    }
                }
            }

            describe("when there are four containers and the network to clean up") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter())
                val container3 = Container("container-3", imageSourceDoesNotMatter())
                val container4 = Container("container-4", imageSourceDoesNotMatter())

                beforeEachTest {
                    cleanupDisplay.onEventPosted(TaskNetworkCreatedEvent(DockerNetwork("some-network")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container1, DockerContainer("container-1-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container2, DockerContainer("container-2-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container3, DockerContainer("container-3-id")))
                    cleanupDisplay.onEventPosted(ContainerCreatedEvent(container4, DockerContainer("container-4-id")))
                }

                on("and none of the containers have been removed yet") {
                    val output = cleanupDisplay.print()

                    it("prints that all of the containers still need to be cleaned up") {
                        assertThat(output, equivalentTo(Text.white(Text("Cleaning up: 4 containers (") + Text.bold("container-1") + Text(", ") + Text.bold("container-2") + Text(", ") + Text.bold("container-3") + Text(" and ") + Text.bold("container-4") + Text(") left to remove..."))))
                    }
                }
            }
        }
    }
})
