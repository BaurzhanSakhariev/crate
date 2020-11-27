/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.expression.reference.doc.lucene;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.common.xcontent.XContentType;

import io.crate.metadata.ColumnIdent;
import io.crate.types.ArrayType;
import io.crate.types.DataType;
import io.crate.types.DoubleType;
import io.crate.types.FloatType;
import io.crate.types.IntegerType;
import io.crate.types.LongType;
import io.crate.types.ObjectType;
import io.crate.types.ShortType;
import io.crate.types.StringType;

public final class SourceParser {

    static Object parseValue(Map<ColumnIdent, DataType<?>> typesByColumn, XContentParser parser, DataType<?> type) throws IOException {
        return switch (type.id()) {
            case ArrayType.ID -> parseArray(parser, type);
            case ObjectType.ID -> parseObject(parser, type);
            case ShortType.ID -> parser.shortValue(true);
            case IntegerType.ID -> parser.intValue();
            case LongType.ID -> parser.longValue();
            case FloatType.ID -> parser.floatValue();
            case DoubleType.ID -> parser.doubleValue();
            case StringType.ID -> parser.text();
            default -> {
                throw new UnsupportedOperationException("parseValue not implemented for " + type);
            }
        };
    }

    static Object parseObject(XContentParser parser, DataType<?> type) {
        throw new UnsupportedOperationException("parseValue not implemented for object");
    }

    static Object parseArray(XContentParser parser, DataType<?> type) {
        throw new UnsupportedOperationException("parseValue not implemented for array");
    }

    public static Map<ColumnIdent, Object> parse(BytesReference bytes, Map<ColumnIdent, DataType<?>> typesByColumn) {
        try (InputStream inputStream = XContentHelper.getUncompressedInputStream(bytes)) {
            XContentParser parser = XContentType.JSON.xContent().createParser(
                NamedXContentRegistry.EMPTY,
                DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                inputStream
            );
            Token token = parser.currentToken();
            if (token == null) {
                token = parser.nextToken();
            }
            if (token == XContentParser.Token.START_OBJECT) {
                token = parser.nextToken();
            }
            HashMap<ColumnIdent, Object> result = new HashMap<>();
            for (; token == XContentParser.Token.FIELD_NAME; token = parser.nextToken()) {
                String fieldName = parser.currentName();
                parser.nextToken();

                ColumnIdent column = new ColumnIdent(fieldName);
                if (token == XContentParser.Token.VALUE_NULL) {
                    result.put(column, null);
                } else {
                    var type = typesByColumn.get(column);
                    if (type == null) {
                        continue;
                    }
                    Object value = parseValue(typesByColumn, parser, type);
                    result.put(column, value);
                }
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}