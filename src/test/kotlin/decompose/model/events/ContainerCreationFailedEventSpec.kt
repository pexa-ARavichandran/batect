package decompose.model.events

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import decompose.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerCreationFailedEventSpec : Spek({
    describe("a 'container creation failed' event") {
        val container = Container("container-1", "/build-dir")
        val event = ContainerCreationFailedEvent(container, "Something went wrong")

        on("getting the message to display to the user") {
            it("returns an appropriate message") {
                assert.that(event.messageToDisplay, equalTo("Could not create container 'container-1': Something went wrong"))
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assert.that(event.toString(), equalTo("ContainerCreationFailedEvent(container: 'container-1', message: 'Something went wrong')"))
            }
        }
    }
})
