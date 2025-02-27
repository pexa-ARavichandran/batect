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

package batect.cli.options.defaultvalues

import batect.cli.options.OptionValueSource

interface DefaultValueProvider<T> {
    val value: PossibleValue<T>
    val description: String

    val valueSource: OptionValueSource
        get() = OptionValueSource.Default
}

sealed class PossibleValue<V> {
    data class Valid<V>(val value: V) : PossibleValue<V>()
    data class Invalid<V>(val message: String) : PossibleValue<V>()
}
