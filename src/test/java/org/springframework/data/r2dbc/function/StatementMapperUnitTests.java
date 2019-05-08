/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.data.r2dbc.function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.domain.BindTarget;
import org.springframework.data.r2dbc.domain.PreparedOperation;
import org.springframework.data.r2dbc.function.StatementMapper.UpdateSpec;
import org.springframework.data.r2dbc.function.query.Criteria;
import org.springframework.data.r2dbc.function.query.Update;

/**
 * Unit tests for {@link DefaultStatementMapper}.
 *
 * @author Mark Paluch
 */
public class StatementMapperUnitTests {

	ReactiveDataAccessStrategy strategy = new DefaultReactiveDataAccessStrategy(PostgresDialect.INSTANCE);
	StatementMapper mapper = strategy.getStatementMapper();

	BindTarget bindTarget = mock(BindTarget.class);

	@Test // gh-64
	public void shouldMapUpdate() {

		UpdateSpec updateSpec = mapper.createUpdate("foo", Update.update("column", "value"));

		PreparedOperation<?> preparedOperation = mapper.getMappedObject(updateSpec);

		assertThat(preparedOperation.toQuery()).isEqualTo("UPDATE foo SET column = $1");

		preparedOperation.bindTo(bindTarget);
		verify(bindTarget).bind(0, "value");
	}

	@Test // gh-64
	public void shouldMapUpdateWithCriteria() {

		UpdateSpec updateSpec = mapper.createUpdate("foo", Update.update("column", "value"))
				.withCriteria(Criteria.where("foo").is("bar"));

		PreparedOperation<?> preparedOperation = mapper.getMappedObject(updateSpec);

		assertThat(preparedOperation.toQuery()).isEqualTo("UPDATE foo SET column = $1 WHERE foo.foo = $2");

		preparedOperation.bindTo(bindTarget);
		verify(bindTarget).bind(0, "value");
		verify(bindTarget).bind(1, "bar");
	}
}
