/*
 * Copyright 2020-2021 the original author or authors.
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
package org.springframework.data.r2dbc.repository.query;

import static org.springframework.data.r2dbc.repository.query.ExpressionQuery.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.data.r2dbc.core.ReactiveDataAccessStrategy;
import org.springframework.data.relational.repository.query.RelationalParameterAccessor;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * {@link ExpressionEvaluatingParameterBinder} allows to evaluate, convert and bind parameters to placeholders within a
 * {@link String}.
 *
 * @author Mark Paluch
 * @since 1.1
 */
class ExpressionEvaluatingParameterBinder {

	private final ExpressionQuery expressionQuery;

	private final ReactiveDataAccessStrategy dataAccessStrategy;

	private final Map<String, Boolean> namedParameters = new ConcurrentHashMap<>();

	/**
	 * Creates new {@link ExpressionEvaluatingParameterBinder}
	 *
	 * @param expressionQuery must not be {@literal null}.
	 * @param dataAccessStrategy must not be {@literal null}.
	 */
	ExpressionEvaluatingParameterBinder(ExpressionQuery expressionQuery, ReactiveDataAccessStrategy dataAccessStrategy) {
		this.expressionQuery = expressionQuery;
		this.dataAccessStrategy = dataAccessStrategy;
	}

	/**
	 * Bind values provided by {@link RelationalParameterAccessor} to placeholders in {@link ExpressionQuery} while
	 * considering potential conversions and parameter types.
	 *
	 * @param bindSpec must not be {@literal null}.
	 * @param parameterAccessor must not be {@literal null}.
	 * @param evaluator must not be {@literal null}.
	 */
	DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec bindSpec,
			RelationalParameterAccessor parameterAccessor, R2dbcSpELExpressionEvaluator evaluator) {

		Object[] values = parameterAccessor.getValues();
		Parameters<?, ?> bindableParameters = parameterAccessor.getBindableParameters();

		DatabaseClient.GenericExecuteSpec bindSpecToUse = bindExpressions(bindSpec, evaluator);
		bindSpecToUse = bindParameters(bindSpecToUse, parameterAccessor.hasBindableNullValue(), values, bindableParameters);

		return bindSpecToUse;
	}

	private DatabaseClient.GenericExecuteSpec bindExpressions(DatabaseClient.GenericExecuteSpec bindSpec,
			R2dbcSpELExpressionEvaluator evaluator) {

		DatabaseClient.GenericExecuteSpec bindSpecToUse = bindSpec;

		for (ParameterBinding binding : expressionQuery.getBindings()) {

			org.springframework.r2dbc.core.Parameter valueForBinding = getBindValue(
					evaluator.evaluate(binding.getExpression()));

			bindSpecToUse = bind(bindSpecToUse, binding.getParameterName(), valueForBinding);
		}

		return bindSpecToUse;
	}

	private DatabaseClient.GenericExecuteSpec bindParameters(DatabaseClient.GenericExecuteSpec bindSpec,
			boolean hasBindableNullValue, Object[] values, Parameters<?, ?> bindableParameters) {

		DatabaseClient.GenericExecuteSpec bindSpecToUse = bindSpec;
		int bindingIndex = 0;

		for (Parameter bindableParameter : bindableParameters) {

			Optional<String> name = bindableParameter.getName();

			if (name.isPresent() && (isNamedParameterReferencedFromQuery(name)) || !expressionQuery.getBindings().isEmpty()) {

				if (!isNamedParameterReferencedFromQuery(name)) {
					continue;
				}

				org.springframework.r2dbc.core.Parameter parameter = getBindValue(values, bindableParameter);

				if (!parameter.isEmpty() || hasBindableNullValue) {
					bindSpecToUse = bind(bindSpecToUse, name.get(), parameter);
				}

				// skip unused named parameters if there is SpEL
			} else {

				org.springframework.r2dbc.core.Parameter parameter = getBindValue(values, bindableParameter);

				if (!parameter.isEmpty() || hasBindableNullValue) {
					bindSpecToUse = bind(bindSpecToUse, bindingIndex++, parameter);
				}
			}
		}

		return bindSpecToUse;
	}

	private org.springframework.r2dbc.core.Parameter getBindValue(Object[] values, Parameter bindableParameter) {

		org.springframework.r2dbc.core.Parameter parameter = org.springframework.r2dbc.core.Parameter
				.fromOrEmpty(values[bindableParameter.getIndex()], bindableParameter.getType());

		return dataAccessStrategy.getBindValue(parameter);
	}

	private static DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, String name,
			org.springframework.r2dbc.core.Parameter parameter) {

		Object value = parameter.getValue();
		if (value == null) {
			return spec.bindNull(name, parameter.getType());
		} else {
			return spec.bind(name, value);
		}
	}

	private static DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, int index,
			org.springframework.r2dbc.core.Parameter parameter) {

		Object value = parameter.getValue();
		if (value == null) {
			return spec.bindNull(index, parameter.getType());
		} else {

			return spec.bind(index, value);
		}
	}

	private org.springframework.r2dbc.core.Parameter getBindValue(org.springframework.r2dbc.core.Parameter bindValue) {
		return dataAccessStrategy.getBindValue(bindValue);
	}

	private boolean isNamedParameterReferencedFromQuery(Optional<String> name) {

		if (!name.isPresent()) {
			return false;
		}

		return namedParameters.computeIfAbsent(name.get(), it -> {

			Pattern namedParameterPattern = Pattern.compile("(\\W)[:#$@]" + Pattern.quote(it) + "(\\W|$)");
			return namedParameterPattern.matcher(expressionQuery.getQuery()).find();
		});
	}

}
