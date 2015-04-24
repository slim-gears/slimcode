// Copyright 2015 Denis Itskovich
// Refer to LICENSE.txt for license details
package com.slimgears.slimrepo.core.internal.sql;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.slimgears.slimrepo.core.interfaces.entities.Entity;
import com.slimgears.slimrepo.core.interfaces.entities.EntityType;
import com.slimgears.slimrepo.core.interfaces.fields.Field;
import com.slimgears.slimrepo.core.interfaces.entities.FieldValueLookup;
import com.slimgears.slimrepo.core.interfaces.fields.NumericField;
import com.slimgears.slimrepo.core.interfaces.conditions.Condition;
import com.slimgears.slimrepo.core.internal.EntityFieldValueMap;
import com.slimgears.slimrepo.core.internal.OrderFieldInfo;
import com.slimgears.slimrepo.core.internal.UpdateFieldInfo;
import com.slimgears.slimrepo.core.internal.query.DeleteQueryParams;
import com.slimgears.slimrepo.core.internal.query.InsertQueryParams;
import com.slimgears.slimrepo.core.internal.query.QueryPagination;
import com.slimgears.slimrepo.core.internal.query.SelectQueryParams;
import com.slimgears.slimrepo.core.internal.query.UpdateQueryParams;

import java.util.Collection;

import static com.google.common.collect.Iterables.transform;

/**
 * Created by Denis on 08-Apr-15
 * <File Description>
 */
public class DefaultSqlStatementBuilder implements SqlStatementBuilder {
    private final PredicateBuilder predicateBuilder;
    private final SyntaxProvider syntaxProvider;

    public DefaultSqlStatementBuilder(PredicateBuilder predicateBuilder, SyntaxProvider syntaxProvider) {
        this.predicateBuilder = predicateBuilder;
        this.syntaxProvider = syntaxProvider;
    }

    @Override
    public <TKey, TEntity extends Entity<TKey>> String countStatement(SelectQueryParams<TKey, TEntity> params, SqlCommand.Parameters sqlParams) {
        return
                selectCountClause() +
                fromClause(params.entityType) +
                whereClause(params.condition, sqlParams) +
                limitClause(params.pagination);
    }

    @Override
    public <TKey, TEntity extends Entity<TKey>> String selectStatement(SelectQueryParams<TKey, TEntity> params, SqlCommand.Parameters sqlParams) {
        return
                selectClause(params.entityType) +
                fromClause(params.entityType) +
                whereClause(params.condition, sqlParams) +
                orderByClause(params.order) +
                limitClause(params.pagination);
    }

    @Override
    public <TKey, TEntity extends Entity<TKey>> String updateStatement(UpdateQueryParams<TKey, TEntity> params, SqlCommand.Parameters sqlParams) {
        return
                updateClause(params.entityType) +
                setClause(params.updates, sqlParams) +
                whereClause(params.condition, sqlParams) +
                limitClause(params.pagination);
    }

    @Override
    public <TKey, TEntity extends Entity<TKey>> String deleteStatement(DeleteQueryParams<TKey, TEntity> params, SqlCommand.Parameters sqlParams) {
        return "DELETE " + fromClause(params.entityType) +
                whereClause(params.condition, sqlParams) +
                limitClause(params.pagination);
    }

    @Override
    public <TKey, TEntity extends Entity<TKey>> String insertStatement(InsertQueryParams<TKey, TEntity> params, SqlCommand.Parameters sqlParams) {
        Iterable<Field> fields = fieldsToInsert(params.entityType);
        return
                insertClause(params.entityType, fields) +
                valuesClause(fields, sqlParams, entitiesToRows(params.entityType, params.entities));
    }

    @Override
    public <TKey, TEntity extends Entity<TKey>> String createTableStatement(EntityType<TKey, TEntity> entityType) {
        return
                "CREATE TABLE IF NOT EXISTS " + tableName(entityType) + "\n" +
                " (" + columnDefinitions(entityType) + ")";
    }

    @Override
    public <TKey, TEntity extends Entity<TKey>> String dropTableStatement(EntityType<TKey, TEntity> entityType) {
        return "DROP TABLE IF EXISTS " + tableName(entityType);
    }

    protected String insertClause(EntityType entityType, Iterable<Field> fields) {
        return "INSERT INTO " +
                tableName(entityType) +
                " (" + Joiner.on(", ").join(fieldNames(fields)) + ")\n";
    }

    protected String valuesClause(final Iterable<Field> fields, final SqlCommand.Parameters parameters, Iterable<FieldValueLookup> rows) {
        return "VALUES " +
                Joiner.on(", ").join(transform(rows, new Function<FieldValueLookup, String>() {
                    @Override
                    public String apply(final FieldValueLookup row) {
                        return "(" +
                                Joiner.on(", ").join(transform(fields,
                                        new Function<Field, String>() {
                                            @Override
                                            public String apply(Field field) {
                                                //noinspection unchecked
                                                return syntaxProvider.substituteParameter(parameters, field.metaInfo().getType(), row.getValue(field));
                                            }
                        })) + ")";
                    }
                }));
    }

    private Iterable<Field> fieldsToInsert(final EntityType entityType) {
        //noinspection unchecked
        return Iterables.filter((Iterable<Field>)entityType.getFields(), new com.google.common.base.Predicate<Field>() {
            @Override
            public boolean apply(Field field) {
                return !isAutoIncremented(entityType, field);
            }
        });
    }

    private String limitClause(QueryPagination pagination) {
        if (pagination == null || (pagination.limit == -1 && pagination.offset == 0)) return "";
        String limitClause = "LIMIT " + pagination.limit;
        return pagination.offset == 0
                ? limitClause + "\n"
                : limitClause + " OFFSET " + pagination.offset + "\n";
    }

    private String whereClause(Condition condition, SqlCommand.Parameters parameters) {
        if (condition == null) return "";
        String strPredicate = predicateBuilder.build(condition, parameters);
        return "WHERE " + strPredicate + "\n";
    }

    private String selectCountClause() {
        return "SELECT COUNT(*)\n";
    }

    private String selectClause(EntityType entityType) {
        return "SELECT " + Joiner.on(", ").join(fieldNames(entityType)) + "\n";
    }

    private String orderByClause(Collection<OrderFieldInfo> orderFields) {
        if (orderFields == null || orderFields.isEmpty()) return "";
        return "ORDER BY " + Joiner.on(", ")
                .join(transform(orderFields,
                        new Function<OrderFieldInfo, String>() {
                            @Override
                            public String apply(OrderFieldInfo orderField) {
                                return fieldName(orderField.field) + " " + (orderField.ascending ? "ASC" : "DESC");
                            }
                        })) + "\n";
    }

    private String setClause(final Collection<UpdateFieldInfo> updateFields, final SqlCommand.Parameters parameters) {
        if (updateFields == null || updateFields.isEmpty()) return "";
        return "SET " + Joiner
                .on(", ")
                .join(transform(updateFields,
                        new Function<UpdateFieldInfo, String>() {
                            @Override
                            public String apply(UpdateFieldInfo updateField) {
                                return fieldName(updateField.field) + " = " + syntaxProvider.substituteParameter(parameters, updateField.field.metaInfo().getType(), updateField.value);
                            }
                        })) + "\n";
    }

    private String updateClause(EntityType entityType) {
        return "UPDATE " + tableName(entityType) + "\n";
    }

    private String fromClause(EntityType entityType) {
        return "FROM " + tableName(entityType) + "\n";
    }

    private String tableName(EntityType entityType) {
        return syntaxProvider.tableName(entityType);
    }

    private Iterable<String> fieldNames(EntityType entityType) {
        //noinspection unchecked
        return fieldNames(entityType.getFields());
    }

    private Iterable<String> fieldNames(Iterable<Field> fields) {
        return transform(fields, new Function<Field, String>() {
            @Override
            public String apply(Field field) {
                return fieldName(field);
            }
        });
    }

    private String columnDefinition(EntityType entityType, Field field) {
        return columnName(field) + " " + columnType(field) + " " + columnConstraints(entityType, field);
    }

    private String fieldName(Field field) {
        return syntaxProvider.fieldName(field);
    }

    private <TKey, TEntity extends Entity<TKey>> Collection<FieldValueLookup> entitiesToRows(final EntityType<TKey, TEntity> entityType, Collection<TEntity> entities) {
        return Collections2.transform(entities, new Function<TEntity, FieldValueLookup>() {
            @Override
            public FieldValueLookup apply(TEntity entity) {
                return new EntityFieldValueMap<>(entityType, entity);
            }
        });
    }

    private String columnDefinitions(final EntityType entityType) {
        return Joiner
                .on(", ")
                .join(transform(entityType.getFields(), new Function<Field, String>() {
                    @Override
                    public String apply(Field field) {
                        return columnDefinition(entityType, field);
                    }
                }));
    }

    private String columnConstraints(EntityType entityType, Field field) {
        if (isAutoIncremented(entityType, field)) return "PRIMARY KEY ASC";
        if (isPrimaryKey(entityType, field)) return "PRIMARY KEY";
        if (!field.metaInfo().isNullable()) return "NOT NULL";
        return "";
    }

    private boolean isPrimaryKey(EntityType entityType, Field field) {
        return entityType.getKeyField() == field;
    }

    private boolean isAutoIncremented(EntityType entityType, Field field) {
        return isPrimaryKey(entityType, field) && field instanceof NumericField;
    }

    private String columnName(Field field) {
        return fieldName(field);
    }

    private String columnType(Field field) {
        return syntaxProvider.typeName(field);
    }
}
