/*
 * Copyright 2021 the original author or authors.
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
package org.springframework.data.r2dbc.convert;

import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.RowMetadata;

import java.lang.reflect.Method;

import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

/**
 * Utility methods for {@link io.r2dbc.spi.RowMetadata}
 *
 * @author Mark Paluch
 * @since 1.3.7
 */
class RowMetadataUtils {

	private static final @Nullable Method getColumnMetadatas = ReflectionUtils.findMethod(RowMetadata.class,
			"getColumnMetadatas");

	/**
	 * Check whether the column {@code name} is contained in {@link RowMetadata}. The check happens case-insensitive.
	 *
	 * @param metadata the metadata object to inspect.
	 * @param name column name.
	 * @return {@code true} if the metadata contains the column {@code name}.
	 */
	public static boolean containsColumn(RowMetadata metadata, String name) {

		Iterable<? extends ColumnMetadata> columns = getColumnMetadata(metadata);

		for (ColumnMetadata columnMetadata : columns) {
			if (name.equalsIgnoreCase(columnMetadata.getName())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Return the {@link Iterable} of {@link ColumnMetadata} from {@link RowMetadata}.
	 *
	 * @param metadata the metadata object to inspect.
	 * @return
	 * @since 1.4.1
	 */
	@SuppressWarnings("unchecked")
	public static Iterable<? extends ColumnMetadata> getColumnMetadata(RowMetadata metadata) {

		if (getColumnMetadatas != null) {
			// Return type of RowMetadata.getColumnMetadatas was updated with R2DBC 0.9.
			return (Iterable<? extends ColumnMetadata>) ReflectionUtils.invokeMethod(getColumnMetadatas, metadata);
		}

		return metadata.getColumnMetadatas();
	}
}
