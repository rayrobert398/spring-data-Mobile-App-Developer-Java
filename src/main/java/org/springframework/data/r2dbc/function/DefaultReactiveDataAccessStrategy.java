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
package org.springframework.data.r2dbc.function;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.convert.CustomConversions.StoreConversions;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.r2dbc.dialect.ArrayColumns;
import org.springframework.data.r2dbc.dialect.BindMarkersFactory;
import org.springframework.data.r2dbc.dialect.Dialect;
import org.springframework.data.r2dbc.domain.OutboundRow;
import org.springframework.data.r2dbc.domain.SettableValue;
import org.springframework.data.r2dbc.function.convert.EntityRowMapper;
import org.springframework.data.r2dbc.function.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.function.convert.R2dbcConverter;
import org.springframework.data.r2dbc.function.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.function.query.UpdateMapper;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.Select;
import org.springframework.data.relational.core.sql.render.NamingStrategies;
import org.springframework.data.relational.core.sql.render.RenderContext;
import org.springframework.data.relational.core.sql.render.RenderNamingStrategy;
import org.springframework.data.relational.core.sql.render.SelectRenderContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Default {@link ReactiveDataAccessStrategy} implementation.
 *
 * @author Mark Paluch
 */
public class DefaultReactiveDataAccessStrategy implements ReactiveDataAccessStrategy {

	private final Dialect dialect;
	private final R2dbcConverter converter;
	private final UpdateMapper updateMapper;
	private final MappingContext<RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty> mappingContext;
	private final StatementMapper statementMapper;

	/**
	 * Creates a new {@link DefaultReactiveDataAccessStrategy} given {@link Dialect}.
	 *
	 * @param dialect the {@link Dialect} to use.
	 */
	public DefaultReactiveDataAccessStrategy(Dialect dialect) {
		this(dialect, createConverter(dialect));
	}

	private static R2dbcConverter createConverter(Dialect dialect) {

		Assert.notNull(dialect, "Dialect must not be null");

		R2dbcCustomConversions customConversions = new R2dbcCustomConversions(
				StoreConversions.of(dialect.getSimpleTypeHolder()), Collections.emptyList());

		RelationalMappingContext context = new RelationalMappingContext();
		context.setSimpleTypeHolder(customConversions.getSimpleTypeHolder());

		return new MappingR2dbcConverter(context, customConversions);
	}

	/**
	 * Creates a new {@link DefaultReactiveDataAccessStrategy} given {@link Dialect} and {@link R2dbcConverter}.
	 *
	 * @param dialect the {@link Dialect} to use.
	 * @param converter must not be {@literal null}.
	 */
	@SuppressWarnings("unchecked")
	public DefaultReactiveDataAccessStrategy(Dialect dialect, R2dbcConverter converter) {

		Assert.notNull(dialect, "Dialect must not be null");
		Assert.notNull(converter, "RelationalConverter must not be null");

		this.converter = converter;
		this.updateMapper = new UpdateMapper(converter);
		this.mappingContext = (MappingContext<RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty>) this.converter
				.getMappingContext();
		this.dialect = dialect;

		RenderContext renderContext = new RenderContext() {
			@Override
			public RenderNamingStrategy getNamingStrategy() {
				return NamingStrategies.asIs();
			}

			@Override
			public SelectRenderContext getSelect() {
				return new SelectRenderContext() {
					@Override
					public Function<Select, ? extends CharSequence> afterSelectList() {
						return it -> "";
					}

					@Override
					public Function<Select, ? extends CharSequence> afterOrderBy(boolean hasOrderBy) {
						return it -> "";
					}
				};
			}
		};

		this.statementMapper = new DefaultStatementMapper(dialect, renderContext, this.updateMapper, this.mappingContext);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getAllColumns(java.lang.Class)
	 */
	@Override
	public List<String> getAllColumns(Class<?> entityType) {

		RelationalPersistentEntity<?> persistentEntity = getPersistentEntity(entityType);

		if (persistentEntity == null) {
			return Collections.singletonList("*");
		}

		List<String> columnNames = new ArrayList<>();
		for (RelationalPersistentProperty property : persistentEntity) {
			columnNames.add(property.getColumnName());
		}

		return columnNames;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getIdentifierColumns(java.lang.Class)
	 */
	@Override
	public List<String> getIdentifierColumns(Class<?> entityType) {

		RelationalPersistentEntity<?> persistentEntity = getRequiredPersistentEntity(entityType);

		List<String> columnNames = new ArrayList<>();
		for (RelationalPersistentProperty property : persistentEntity) {

			if (property.isIdProperty()) {
				columnNames.add(property.getColumnName());
			}
		}

		return columnNames;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getOutboundRow(java.lang.Object)
	 */
	public OutboundRow getOutboundRow(Object object) {

		Assert.notNull(object, "Entity object must not be null!");

		OutboundRow row = new OutboundRow();

		this.converter.write(object, row);

		RelationalPersistentEntity<?> entity = getRequiredPersistentEntity(ClassUtils.getUserClass(object));

		for (RelationalPersistentProperty property : entity) {

			SettableValue value = row.get(property.getColumnName());
			if (shouldConvertArrayValue(property, value)) {

				SettableValue writeValue = getArrayValue(value, property);
				row.put(property.getColumnName(), writeValue);
			}
		}

		return row;
	}

	private boolean shouldConvertArrayValue(RelationalPersistentProperty property, SettableValue value) {
		return value != null && value.hasValue() && property.isCollectionLike();
	}

	private SettableValue getArrayValue(SettableValue value, RelationalPersistentProperty property) {

		ArrayColumns arrayColumns = this.dialect.getArraySupport();

		if (!arrayColumns.isSupported()) {

			throw new InvalidDataAccessResourceUsageException(
					"Dialect " + this.dialect.getClass().getName() + " does not support array columns");
		}

		return SettableValue.fromOrEmpty(this.converter.getArrayValue(arrayColumns, property, value.getValue()),
				property.getActualType());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getRowMapper(java.lang.Class)
	 */
	@Override
	public <T> BiFunction<Row, RowMetadata, T> getRowMapper(Class<T> typeToRead) {
		return new EntityRowMapper<>(typeToRead, this.converter);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getTableName(java.lang.Class)
	 */
	@Override
	public String getTableName(Class<?> type) {
		return getRequiredPersistentEntity(type).getTableName();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getStatementMapper()
	 */
	@Override
	public StatementMapper getStatementMapper() {
		return this.statementMapper;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getBindMarkersFactory()
	 */
	@Override
	public BindMarkersFactory getBindMarkersFactory() {
		return this.dialect.getBindMarkersFactory();
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.r2dbc.function.ReactiveDataAccessStrategy#getConverter()
	 */
	public R2dbcConverter getConverter() {
		return this.converter;
	}

	public MappingContext<RelationalPersistentEntity<?>, ? extends RelationalPersistentProperty> getMappingContext() {
		return this.mappingContext;
	}

	private RelationalPersistentEntity<?> getRequiredPersistentEntity(Class<?> typeToRead) {
		return this.mappingContext.getRequiredPersistentEntity(typeToRead);
	}

	@Nullable
	private RelationalPersistentEntity<?> getPersistentEntity(Class<?> typeToRead) {
		return this.mappingContext.getPersistentEntity(typeToRead);
	}
}
