/*
 *     This file is part of ToroDB.
 *
 *     ToroDB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ToroDB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with ToroDB. If not, see <http://www.gnu.org/licenses/>.
 *
 *     Copyright (c) 2014, 8Kdata Technology
 *     
 */


package com.torodb.torod.db.wrappers.postgresql;

import com.torodb.torod.db.wrappers.ArraySerializator;
import com.torodb.torod.db.wrappers.DatabaseInterface;
import com.torodb.torod.db.wrappers.converters.BasicTypeToSqlType;
import com.torodb.torod.db.wrappers.tables.CollectionsTable;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
@Singleton
public class PostgresqlDatabaseInterface implements DatabaseInterface {

    private static final long serialVersionUID = 484638503;

    private final BasicTypeToSqlType basicTypeToSqlType;

    private static class ArraySerializatorHolder {
        private static final ArraySerializator INSTANCE = new JsonbArraySerializator();
    }

    @Override
    public @Nonnull ArraySerializator arraySerializator() {
        return ArraySerializatorHolder.INSTANCE;
    }

    @Inject
    public PostgresqlDatabaseInterface(BasicTypeToSqlType basicTypeToSqlType) {
        this.basicTypeToSqlType = basicTypeToSqlType;
    }

    @Override
    public @Nonnull String escapeSchemaName(@Nonnull String collection) throws IllegalArgumentException {
        return filter(collection);
    }

    @Override
    public @Nonnull String escapeAttributeName(@Nonnull String attributeName) throws IllegalArgumentException {
        return filter(attributeName);
    }

    @Override
    public @Nonnull String escapeIndexName(@Nonnull String indexName) throws IllegalArgumentException {
        return filter(indexName);
    }

    private static String filter(String str) {
        if (str.length() > 63) {
            throw new IllegalArgumentException(str + " is too long to be a "
                    + "valid PostgreSQL name. By default names must be shorter "
                    + "than 64, but it has " + str.length() + " characters");
        }
        Pattern quotesPattern = Pattern.compile("(\"+)");
        Matcher matcher = quotesPattern.matcher(str);
        while (matcher.find()) {
            if (((matcher.end() - matcher.start()) & 1) == 1) { //lenght is uneven
                throw new IllegalArgumentException("The name '" + str + "' is"
                        + "illegal because contains an open quote at " + matcher.start());
            }
        }

        return str;
    }

    @Override
    public @Nonnull BasicTypeToSqlType getBasicTypeToSqlType() {
        return basicTypeToSqlType;
    }

    @Override
    public @Nonnull String createCollectionsTableStatement(@Nonnull String schemaName, @Nonnull String tableName) {
        return new StringBuilder()
                .append("CREATE TABLE ")
                .append("\"").append(schemaName).append("\"")
                .append(".")
                .append("\"").append(tableName).append("\"")
                .append(" (")
                .append(CollectionsTable.TableFields.NAME.name()).append("             varchar     PRIMARY KEY     ,")
                .append(CollectionsTable.TableFields.SCHEMA.name()).append("           varchar     NOT NULL UNIQUE ,")
                .append(CollectionsTable.TableFields.CAPPED.name()).append("           boolean     NOT NULL        ,")
                .append(CollectionsTable.TableFields.MAX_SIZE.name()).append("         int         NOT NULL        ,")
                .append(CollectionsTable.TableFields.MAX_ELEMENTS.name()).append("     int         NOT NULL        ,")
                .append(CollectionsTable.TableFields.OTHER.name()).append("            jsonb                       ,")
                .append(CollectionsTable.TableFields.STORAGE_ENGINE.name()).append("   varchar     NOT NULL        ")
                .append(")")
                .toString();
    }

    @Override
    public @Nonnull String createSchemaStatement(@Nonnull String schemaName) {
        return new StringBuilder().append("CREATE SCHEMA ").append("\"").append(schemaName).append("\"").toString();
    }
}
