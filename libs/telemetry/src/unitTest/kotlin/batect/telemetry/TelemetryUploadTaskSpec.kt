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

package batect.telemetry

import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withAdditionalData
import batect.testutils.logging.withAdditionalDataAndAnyValue
import batect.testutils.logging.withException
import batect.testutils.logging.withLogMessage
import batect.testutils.logging.withSeverity
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.serialization.json.Json
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

object TelemetryUploadTaskSpec : Spek({
    describe("a telemetry upload task") {
        val telemetryConsent by createForEachTest { mock<TelemetryConsent>() }
        val telemetryUploadQueue by createForEachTest { mock<TelemetryUploadQueue>() }
        val abacusClient by createForEachTest { mock<AbacusClient>() }

        @Suppress("UNCHECKED_CAST")
        val telemetrySessionBuilder by createForEachTest {
            mock<TelemetrySessionBuilder> {
                on { addSpan<Unit>(any(), any()) } doAnswer { invocation ->
                    val process = invocation.arguments[1] as ((TelemetrySpanBuilder) -> Unit)
                    process(mock())
                }
            }
        }

        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("Test logger", logSink) }
        val now = ZonedDateTime.of(2020, 5, 13, 6, 30, 0, 0, ZoneOffset.UTC)
        val timeSource: TimeSource = { now }
        var ranOnThread = false

        val threadRunner: ThreadRunner by createForEachTest {
            { block: BackgroundProcess ->
                ranOnThread = true
                block()
            }
        }

        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val uploadTask by createForEachTest { TelemetryUploadTask(telemetryConsent, telemetryUploadQueue, abacusClient, telemetrySessionBuilder, logger, threadRunner, timeSource) }

        beforeEachTest { ranOnThread = false }

        fun runWithSessions(vararg sessions: Path) {
            whenever(telemetryUploadQueue.getAll()).doReturn(sessions.toSet())

            uploadTask.start()
        }

        fun createSession(path: Path, startTime: ZonedDateTime): ByteArray {
            val session = TelemetrySession(
                UUID.fromString("8a1058f8-e41e-4c78-aa42-663b78d15122"),
                UUID.fromString("07ab839b-ac26-475a-966a-77d18d00ac61"),
                startTime,
                startTime.plusSeconds(25),
                "my-app-$path",
                "1.0.0",
                emptyMap(),
                emptyList(),
                emptyList()
            )

            val bytes = Json.Default.encodeToString(TelemetrySession.serializer(), session).toByteArray(Charsets.UTF_8)

            Files.write(path, bytes)

            return bytes
        }

        given("telemetry is not enabled") {
            beforeEachTest {
                whenever(telemetryConsent.telemetryAllowed).thenReturn(false)

                runWithSessions(fileSystem.getPath("some-session.json"))
            }

            it("does not start a background thread") {
                assertThat(ranOnThread, equalTo(false))
            }

            it("does not query the upload queue") {
                verifyNoInteractions(telemetryUploadQueue)
            }

            it("does not upload anything") {
                verifyNoInteractions(abacusClient)
            }

            it("logs a message explaining that telemetry is not allowed") {
                assertThat(logSink, hasMessage(withLogMessage("Telemetry not allowed, not starting telemetry upload task.") and withSeverity(Severity.Info)))
            }
        }

        given("telemetry is enabled") {
            beforeEachTest {
                whenever(telemetryConsent.telemetryAllowed).thenReturn(true)
            }

            given("there are no sessions in the queue") {
                beforeEachTest {
                    runWithSessions()
                }

                it("starts a background thread") {
                    assertThat(ranOnThread, equalTo(true))
                }

                it("logs a message explaining that there are no sessions to upload") {
                    assertThat(logSink, hasMessage(withLogMessage("No sessions to upload.") and withSeverity(Severity.Info)))
                }
            }

            given("there is a single session in the queue") {
                val sessionPath by createForEachTest { fileSystem.getPath("session-1.json") }

                given("uploading the session succeeds") {
                    val sessionBytes by createForEachTest { createSession(sessionPath, now.minusDays(50)) }

                    beforeEachTest {
                        runWithSessions(sessionPath)
                    }

                    it("starts a background thread") {
                        assertThat(ranOnThread, equalTo(true))
                    }

                    it("uploads the session with the default timeout before deleting it") {
                        inOrder(abacusClient, telemetryUploadQueue) {
                            verify(abacusClient).upload(sessionBytes)
                            verify(telemetryUploadQueue).pop(sessionPath)
                        }
                    }

                    it("logs a message confirming that the session has been uploaded successfully") {
                        assertThat(
                            logSink,
                            hasMessage(
                                withLogMessage("Session uploaded successfully.")
                                    and withSeverity(Severity.Info)
                                    and withAdditionalData("sessionPath", sessionPath.toString())
                            )
                        )
                    }
                }

                given("uploading the session fails") {
                    val exception = AbacusClientException("Something went wrong.")

                    beforeEachTest {
                        whenever(abacusClient.upload(any(), anyOrNull())).doThrow(exception)
                    }

                    given("the session is less than 30 days old") {
                        val sessionBytes by createForEachTest { createSession(sessionPath, now.minusDays(29)) }

                        beforeEachTest {
                            runWithSessions(sessionPath)
                        }

                        it("starts a background thread") {
                            assertThat(ranOnThread, equalTo(true))
                        }

                        it("attempts to upload the session") {
                            verify(abacusClient).upload(sessionBytes)
                        }

                        it("does not delete the session") {
                            verify(telemetryUploadQueue, never()).pop(any())
                        }

                        it("logs a warning that the upload failed") {
                            assertThat(
                                logSink,
                                hasMessage(
                                    withLogMessage("Session upload failed. Session is less than 30 days old and so won't be deleted.")
                                        and withSeverity(Severity.Warning)
                                        and withAdditionalData("sessionPath", sessionPath.toString())
                                        and withException(exception)
                                )
                            )
                        }

                        it("reports the exception in telemetry") {
                            verify(telemetrySessionBuilder).addEvent(
                                CommonEvents.UnhandledException,
                                mapOf(
                                    CommonAttributes.Exception to AttributeValue(exception),
                                    CommonAttributes.ExceptionCaughtAt to AttributeValue("batect.telemetry.TelemetryUploadTask.upload"),
                                    CommonAttributes.IsUserFacingException to AttributeValue(false)
                                )
                            )
                        }
                    }

                    given("the session is more than 30 days old") {
                        val sessionBytes by createForEachTest { createSession(sessionPath, now.minusDays(31)) }

                        beforeEachTest {
                            runWithSessions(sessionPath)
                        }

                        it("starts a background thread") {
                            assertThat(ranOnThread, equalTo(true))
                        }

                        it("attempts to upload the session") {
                            verify(abacusClient).upload(sessionBytes)
                        }

                        it("deletes the session") {
                            verify(telemetryUploadQueue).pop(sessionPath)
                        }

                        it("logs a warning that the upload failed") {
                            assertThat(
                                logSink,
                                hasMessage(
                                    withLogMessage("Session upload failed. Session is more than 30 days old and so has been deleted.")
                                        and withSeverity(Severity.Warning)
                                        and withAdditionalData("sessionPath", sessionPath.toString())
                                        and withException(exception)
                                )
                            )
                        }

                        it("reports the exception in telemetry") {
                            verify(telemetrySessionBuilder).addEvent(
                                CommonEvents.UnhandledException,
                                mapOf(
                                    CommonAttributes.Exception to AttributeValue(exception),
                                    CommonAttributes.ExceptionCaughtAt to AttributeValue("batect.telemetry.TelemetryUploadTask.upload"),
                                    CommonAttributes.IsUserFacingException to AttributeValue(false)
                                )
                            )
                        }

                        it("reports the fact that it deleted the exception in telemetry") {
                            verify(telemetrySessionBuilder).addEvent("DeletedOldTelemetrySessionThatFailedToUpload", emptyMap())
                        }
                    }

                    given("the session cannot be parsed") {
                        val sessionBytes = byteArrayOf(0x01, 0x02, 0x03)

                        beforeEachTest {
                            Files.write(sessionPath, sessionBytes)

                            runWithSessions(sessionPath)
                        }

                        it("starts a background thread") {
                            assertThat(ranOnThread, equalTo(true))
                        }

                        it("attempts to upload the session") {
                            verify(abacusClient).upload(sessionBytes)
                        }

                        it("does not delete the session") {
                            verify(telemetryUploadQueue, never()).pop(any())
                        }

                        it("logs a warning that both uploading the session and parsing it failed") {
                            assertThat(
                                logSink,
                                hasMessage(
                                    withLogMessage("Session upload failed, and parsing session to determine age failed.")
                                        and withSeverity(Severity.Error)
                                        and withAdditionalData("sessionPath", sessionPath.toString())
                                        and withException("uploadException", exception)
                                        and withAdditionalDataAndAnyValue("parsingException")
                                )
                            )
                        }

                        it("reports the exception in telemetry") {
                            verify(telemetrySessionBuilder).addEvent(
                                CommonEvents.UnhandledException,
                                mapOf(
                                    CommonAttributes.Exception to AttributeValue(exception),
                                    CommonAttributes.ExceptionCaughtAt to AttributeValue("batect.telemetry.TelemetryUploadTask.upload"),
                                    CommonAttributes.IsUserFacingException to AttributeValue(false)
                                )
                            )
                        }
                    }
                }
            }

            given("there are two sessions in the queue") {
                val session1Path by createForEachTest { fileSystem.getPath("session-1.json") }
                val session2Path by createForEachTest { fileSystem.getPath("session-2.json") }

                given("uploading both of them succeeds") {
                    val session1Bytes by createForEachTest { createSession(session1Path, now.minusDays(50)) }
                    val session2Bytes by createForEachTest { createSession(session2Path, now.minusDays(50)) }

                    beforeEachTest {
                        runWithSessions(session1Path, session2Path)
                    }

                    it("starts a background thread") {
                        assertThat(ranOnThread, equalTo(true))
                    }

                    it("uploads both sessions") {
                        verify(abacusClient).upload(session1Bytes)
                        verify(abacusClient).upload(session2Bytes)
                    }

                    it("deletes both sessions") {
                        verify(telemetryUploadQueue).pop(session1Path)
                        verify(telemetryUploadQueue).pop(session2Path)
                    }
                }

                given("uploading one of them fails") {
                    val session1Bytes by createForEachTest { createSession(session1Path, now.minusDays(20)) }
                    val session2Bytes by createForEachTest { createSession(session2Path, now.minusDays(20)) }

                    beforeEachTest {
                        whenever(abacusClient.upload(session2Bytes)).thenThrow(AbacusClientException("Something went wrong."))

                        runWithSessions(session1Path, session2Path)
                    }

                    it("attempts to upload both sessions") {
                        verify(abacusClient).upload(session1Bytes)
                        verify(abacusClient).upload(session2Bytes)
                    }

                    it("deletes the session that succeeded") {
                        verify(telemetryUploadQueue).pop(session1Path)
                    }

                    it("does not delete the session that failed") {
                        verify(telemetryUploadQueue, never()).pop(session2Path)
                    }
                }
            }

            given("there are more than five sessions in the queue") {
                val session1Path by createForEachTest { fileSystem.getPath("session-1.json") }
                val session2Path by createForEachTest { fileSystem.getPath("session-2.json") }
                val session3Path by createForEachTest { fileSystem.getPath("session-3.json") }
                val session4Path by createForEachTest { fileSystem.getPath("session-4.json") }
                val session5Path by createForEachTest { fileSystem.getPath("session-5.json") }
                val session6Path by createForEachTest { fileSystem.getPath("session-6.json") }

                given("there are no errors while uploading any sessions") {
                    val sessionsUploadedFirst by createForEachTest { mutableSetOf<Path>() }

                    beforeEachTest {
                        Files.write(session1Path, byteArrayOf(0x01))
                        Files.write(session2Path, byteArrayOf(0x02))
                        Files.write(session3Path, byteArrayOf(0x03))
                        Files.write(session4Path, byteArrayOf(0x04))
                        Files.write(session5Path, byteArrayOf(0x05))
                        Files.write(session6Path, byteArrayOf(0x06))

                        val sessionUploadOrder = mutableListOf<Path>()

                        whenever(abacusClient.upload(any(), anyOrNull())).then { invocation ->
                            val bytes = invocation.arguments[0] as ByteArray

                            val session = when (bytes[0]) {
                                0x01.toByte() -> session1Path
                                0x02.toByte() -> session2Path
                                0x03.toByte() -> session3Path
                                0x04.toByte() -> session4Path
                                0x05.toByte() -> session5Path
                                0x06.toByte() -> session6Path
                                else -> throw RuntimeException("Unknown session uploaded.")
                            }

                            sessionUploadOrder.add(session)
                        }

                        for (i in 1..100) {
                            sessionUploadOrder.clear()

                            runWithSessions(session1Path, session2Path, session3Path)

                            sessionsUploadedFirst.add(sessionUploadOrder.first())
                        }
                    }

                    it("uploads them in a random order") {
                        assertThat(sessionsUploadedFirst, equalTo(setOf(session1Path, session2Path, session3Path)))
                    }
                }

                given("all sessions fail to upload") {
                    beforeEachTest {
                        createSession(session1Path, now)
                        createSession(session2Path, now)
                        createSession(session3Path, now)
                        createSession(session4Path, now)
                        createSession(session5Path, now)
                        createSession(session6Path, now)

                        whenever(abacusClient.upload(any(), anyOrNull())).thenThrow(AbacusClientException("Something went wrong."))

                        runWithSessions(session1Path, session2Path, session3Path, session4Path, session5Path, session6Path)
                    }

                    it("stops attempting to upload sessions after the fifth upload failure") {
                        verify(abacusClient, times(5)).upload(any(), anyOrNull())
                    }
                }
            }
        }
    }
})
