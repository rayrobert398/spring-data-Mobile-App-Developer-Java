/*
 * Copyright 2019-2020 the original author or authors.
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
package org.springframework.data.r2dbc.core;

import io.r2dbc.spi.ConnectionFactory;

import javax.sql.DataSource;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.data.r2dbc.testing.ExternalDatabase;
import org.springframework.data.r2dbc.testing.MySqlTestSupport;

/**
 * Integration tests for {@link DatabaseClient} against MySQL using Jasync MySQL.
 *
 * @author Mark Paluch
 */
public class JasyncMySqlDatabaseClientIntegrationTests extends AbstractDatabaseClientIntegrationTests {

	@ClassRule public static final ExternalDatabase database = MySqlTestSupport.database();

	@Override
	protected DataSource createDataSource() {
		return MySqlTestSupport.createDataSource(database);
	}

	@Override
	protected ConnectionFactory createConnectionFactory() {
		return MySqlTestSupport.createJasyncConnectionFactory(database);
	}

	@Override
	protected String getCreateTableStatement() {
		return MySqlTestSupport.CREATE_TABLE_LEGOSET;
	}

	@Override
	@Ignore("Jasync currently uses its own exceptions, see jasync-sql/jasync-sql#106")
	@Test
	public void shouldTranslateDuplicateKeyException() {}

}
