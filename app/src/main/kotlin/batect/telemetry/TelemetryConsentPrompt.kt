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

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.os.ConsoleInfo
import batect.ui.Console
import batect.ui.OutputStyle
import batect.ui.Prompt
import batect.ui.YesNoAnswer

class TelemetryConsentPrompt(
    private val configurationStore: TelemetryConfigurationStore,
    private val telemetryConsent: TelemetryConsent,
    private val commandLineOptions: CommandLineOptions,
    private val consoleInfo: ConsoleInfo,
    private val ciEnvironmentDetector: CIEnvironmentDetector,
    private val console: Console,
    private val prompt: Prompt
) {
    fun askForConsentIfRequired() {
        if (!shouldPromptForConsent) {
            return
        }

        console.println("Batect can collect anonymous environment, usage and performance information.")
        console.println("This information does not include personal or sensitive information, and is used only to help improve Batect.")
        console.println("More information is available at https://batect.dev/privacy, including details of what information is collected and a formal privacy policy.")
        console.println()

        if (consoleInfo.stdinIsTTY && !ciEnvironmentDetector.detect().suspectRunningOnCI) {
            promptForResponse()
            console.println()
        } else {
            console.println("It looks like Batect is running in a non-interactive session, so it can't ask for permission to collect and report this information. To suppress this message:")
            console.println("* To allow collection of data, set the BATECT_ENABLE_TELEMETRY environment variable to 'true', or run './batect --${CommandLineOptionsParser.permanentlyEnableTelemetryFlagName}'.")
            console.println("* To prevent collection of data, set the BATECT_ENABLE_TELEMETRY environment variable to 'false', or run './batect --${CommandLineOptionsParser.permanentlyDisableTelemetryFlagName}'.")
            console.println()
            console.println("No data will be collected for this session.")
            console.println()
        }
    }

    private val shouldPromptForConsent: Boolean
        get() {
            if (configurationStore.currentConfiguration.state != ConsentState.None) return false
            if (telemetryConsent.forbiddenByProjectConfig) return false
            if (commandLineOptions.disableTelemetry != null) return false
            if (commandLineOptions.permanentlyEnableTelemetry) return false
            if (commandLineOptions.permanentlyDisableTelemetry) return false
            if (commandLineOptions.generateShellTabCompletionScript != null) return false
            if (commandLineOptions.generateShellTabCompletionTaskInformation != null) return false
            if (commandLineOptions.requestedOutputStyle == OutputStyle.Quiet) return false

            return true
        }

    private fun promptForResponse() {
        val consentState = when (prompt.askYesNoQuestion("Is it OK for Batect to collect this information?")) {
            YesNoAnswer.Yes -> ConsentState.TelemetryAllowed
            YesNoAnswer.No -> ConsentState.TelemetryDisabled
        }

        val newConfiguration = configurationStore.currentConfiguration.copy(state = consentState)
        configurationStore.saveConfiguration(newConfiguration)
    }
}
