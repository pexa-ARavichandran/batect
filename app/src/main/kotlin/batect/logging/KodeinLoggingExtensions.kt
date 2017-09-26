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

package batect.logging

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.With
import com.github.salomonbrys.kodein.bindings.NoArgBindingKodein
import com.github.salomonbrys.kodein.bindings.SingletonBinding
import com.github.salomonbrys.kodein.erased
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton

inline fun <reified T : Any> Kodein.Builder.singletonWithLogger(noinline creator: NoArgBindingKodein.(Logger) -> T): SingletonBinding<T> {
    return singleton {
        creator(With(erased(), T::class).instance())
    }
}
