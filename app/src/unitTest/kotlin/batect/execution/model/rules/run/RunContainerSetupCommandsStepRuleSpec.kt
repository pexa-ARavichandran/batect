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

package batect.execution.model.rules.run

import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.RunContainerSetupCommandsStep
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunContainerSetupCommandsStepRuleSpec : Spek({
    describe("a run container setup commands step rule") {
        val container = Container("the-container", imageSourceDoesNotMatter())
        val rule = RunContainerSetupCommandsStepRule(container)

        given("the container has become healthy") {
            val dockerContainer = DockerContainer("some-container-id")
            val events = setOf(
                ContainerCreatedEvent(container, dockerContainer),
                ContainerStartedEvent(container),
                ContainerBecameHealthyEvent(container)
            )

            on("evaluating the rule") {
                val result = rule.evaluate(events)

                it("returns a 'run container setup commands' step") {
                    assertThat(
                        result,
                        equalTo(
                            TaskStepRuleEvaluationResult.Ready(
                                RunContainerSetupCommandsStep(container, dockerContainer)
                            )
                        )
                    )
                }
            }
        }

        given("another container has become healthy") {
            val otherContainer = Container("the-other-container", imageSourceDoesNotMatter())
            val events = setOf(ContainerBecameHealthyEvent(otherContainer))

            on("evaluating the rule") {
                val result = rule.evaluate(events)

                it("indicates that the step is not yet ready") {
                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                }
            }
        }

        given("the container has not started") {
            on("evaluating the rule") {
                val result = rule.evaluate(emptySet())

                it("indicates that the step is not yet ready") {
                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                }
            }
        }

        on("attaching it to a log message") {
            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(rule),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${rule::class.qualifiedName}",
                        |   "container": "the-container"
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
