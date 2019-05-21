/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.r2dbc.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.springframework.data.r2dbc.query.Criteria
import reactor.core.publisher.Mono

/**
 * Unit tests for [Criteria.CriteriaStep] extensions.
 *
 * @author Jonas Bark
 */
class CriteriaStepExtensionsTests {

	@Test // gh-122
	fun eqIsCriteriaStep() {

		val spec = mockk<Criteria.CriteriaStep>()
		val eqSpec = mockk<Criteria>()

		every { spec.`is`("test") } returns eqSpec

		runBlocking {
			assertThat(spec isEquals "test").isEqualTo(eqSpec)
		}

		verify {
			spec.`is`("test")
		}
	}
}
