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

package batect.journeytests.cleanup

import batect.journeytests.testutils.ApplicationRunner
import batect.journeytests.testutils.Docker
import batect.journeytests.testutils.exitCode
import batect.journeytests.testutils.output
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.platformLineSeparator
import batect.testutils.runBeforeGroup
import ch.tutteli.atrium.api.fluent.en_GB.contains
import ch.tutteli.atrium.api.fluent.en_GB.notToEqual
import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toContain
import ch.tutteli.atrium.api.fluent.en_GB.toContainRegex
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.InputStreamReader

object DontCleanupAfterSuccessJourneyTest : Spek({
    describe("a task with a prerequisite") {
        val runner by createForGroup { ApplicationRunner("task-with-prerequisite") }
        val cleanupCommands by createForGroup { mutableListOf<String>() }
        val containersBeforeTest by runBeforeGroup { Docker.getAllCreatedContainers() }
        val networksBeforeTest by runBeforeGroup { Docker.getAllNetworks() }

        afterGroup {
            cleanupCommands.forEach {
                val commandLine = it.trim().split(" ")

                val exitCode = ProcessBuilder(commandLine)
                    .start()
                    .waitFor()

                expect(exitCode).toEqual(0)
            }

            val containersAfterTest = Docker.getAllCreatedContainers()
            val orphanedContainers = containersAfterTest - containersBeforeTest
            expect(orphanedContainers).toBeEmpty()

            val networksAfterTest = Docker.getAllNetworks()
            val orphanedNetworks = networksAfterTest - networksBeforeTest
            expect(orphanedNetworks).toBeEmpty()
        }

        on("running that task with the '--no-cleanup-on-success' option") {
            val result by runBeforeGroup { runner.runApplication(listOf("--no-cleanup-after-success", "--no-color", "do-stuff")) }
            val commandsRegex = """For container build-env, view its output by running '(?<logsCommand>docker logs (?<id>.*))', or run a command in the container with '(.*)'\.""".toRegex()
            val cleanupRegex = """Once you have finished using the containers, clean up all temporary resources created by Batect by running:$platformLineSeparator(?<command>(.|$platformLineSeparator)+)$platformLineSeparator""".toRegex()

            beforeGroup {
                val cleanupCommand = cleanupRegex.find(result.output)?.groups?.get("command")?.value

                if (cleanupCommand != null) {
                    cleanupCommands.addAll(cleanupCommand.split("\n"))
                }
            }

            it("prints the output from the main task") {
                expect(result).output().toContain("This is some output from the main task\n")
            }

            it("prints the output from the prerequisite task") {
                expect(result).output().toContain("This is some output from the build task\n")
            }

            it("returns a non-zero exit code") {
                expect(result).exitCode().notToEqual(0)
            }

            it("does not return the exit code from the task") {
                expect(result).exitCode().notToEqual(123)
            }

            it("prints a message explaining how to see the logs of the container and how to run a command in the container") {
                expect(result).output().toContainRegex(commandsRegex)
            }

            it("prints a message explaining how to clean up any containers left behind") {
                expect(result).output().toContainRegex(cleanupRegex)
            }

            it("does not delete the container") {
                val containerId = commandsRegex.find(result.output)?.groups?.get("id")?.value

                expect(containerId).notToEqualNull()

                val inspectProcess = ProcessBuilder("docker", "inspect", containerId, "--format", "{{.State.Status}}")
                    .redirectErrorStream(true)
                    .start()

                inspectProcess.waitFor()
                expect(inspectProcess.exitValue()).toEqual(0)

                val output = InputStreamReader(inspectProcess.inputStream).readText().trim()
                expect(output).toEqual("exited")
            }

            it("the command given to view the logs displays the logs from the container") {
                val logsCommand = commandsRegex.find(result.output)?.groups?.get("logsCommand")?.value

                expect(logsCommand).notToEqualNull()

                val logsProcess = ProcessBuilder(logsCommand!!.trim().split(" "))
                    .redirectErrorStream(true)
                    .start()

                logsProcess.waitFor()
                expect(logsProcess.exitValue()).toEqual(0)

                val output = InputStreamReader(logsProcess.inputStream).readText().trim()
                expect(output).toEqual("This is some output from the main task")
            }
        }
    }
})
