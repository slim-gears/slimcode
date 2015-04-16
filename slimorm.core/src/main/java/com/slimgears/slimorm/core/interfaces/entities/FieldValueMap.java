// Copyright 2015 Denis Itskovich
// Refer to LICENSE.txt for license details
package com.slimgears.slimorm.core.interfaces.entities;

import com.slimgears.slimorm.core.interfaces.fields.Field;

/**
 * Created by Denis on 09-Apr-15
 * <File Description>
 */
public interface FieldValueMap<TEntity> extends FieldValueLookup<TEntity> {
    <T> FieldValueMap<TEntity> putValue(Field<TEntity, T> field, T value);
}