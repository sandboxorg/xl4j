/**
 * Copyright (C) 2016 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.v1.typeconvert.converters;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import com.mcleodmoores.xl4j.v1.api.core.Excel;
import com.mcleodmoores.xl4j.v1.api.typeconvert.AbstractTypeConverter;
import com.mcleodmoores.xl4j.v1.api.typeconvert.ExcelToJavaTypeMapping;
import com.mcleodmoores.xl4j.v1.api.typeconvert.TypeConverter;
import com.mcleodmoores.xl4j.v1.api.typeconvert.TypeConverterRegistry;
import com.mcleodmoores.xl4j.v1.api.values.XLArray;
import com.mcleodmoores.xl4j.v1.api.values.XLString;
import com.mcleodmoores.xl4j.v1.api.values.XLValue;
import com.mcleodmoores.xl4j.v1.util.ArgumentChecker;
import com.mcleodmoores.xl4j.v1.util.ConverterUtils;
import com.mcleodmoores.xl4j.v1.util.XL4JRuntimeException;

/**
 * Type converter for sets to {@link XLArray}.
 */
public final class Set2XLArrayTypeConverter extends AbstractTypeConverter {
  /** The priority */
  private static final int PRIORITY = 6;
  /** The Excel context */
  private final Excel _excel;

  /**
   * Default constructor.
   *
   * @param excel
   *          the excel context object, used to access the type converter
   *          registry, not null
   */
  public Set2XLArrayTypeConverter(final Excel excel) {
    super(Set.class, XLArray.class, PRIORITY);
    _excel =  ArgumentChecker.notNull(excel, "excel");
  }

  @Override
  public Object toXLValue(final Object from) {
    ArgumentChecker.notNull(from, "from");
    if (!Set.class.isAssignableFrom(from.getClass())) {
      throw new XL4JRuntimeException("\"from\" parameter must be a Set");
    }
    final Set<?> fromSet = (Set<?>) from;
    if (fromSet.isEmpty()) {
      return XLArray.of(new XLValue[1][1]);
    }
    // we know the length is > 0
    final XLValue[][] toArr = new XLValue[fromSet.size()][1];
    TypeConverter lastConverter = null;
    Class<?> lastClass = null;
    final TypeConverterRegistry typeConverterRegistry = _excel.getTypeConverterRegistry();
    final Iterator<?> iter = fromSet.iterator();
    for (int i = 0; i < fromSet.size(); i++) {
      final Object nextEntry = iter.next();
      if (lastConverter == null || !nextEntry.getClass().equals(lastClass)) {
        lastClass = nextEntry.getClass();
        lastConverter = typeConverterRegistry.findConverter(lastClass);
      }
      final XLValue xlValue = (XLValue) lastConverter.toXLValue(nextEntry);
      toArr[i][0] = xlValue;
    }
    return XLArray.of(toArr);
  }

  @Override
  public Object toJavaObject(final Type expectedType, final Object from) {
    ArgumentChecker.notNull(from, "from");
    final Type type;
    if (expectedType instanceof Class) {
      if (!Set.class.isAssignableFrom((Class<?>) expectedType)) {
        throw new XL4JRuntimeException("expectedType is not a Set");
      }
      type = Object.class;
    } else if (expectedType instanceof ParameterizedType) {
      final ParameterizedType parameterizedType = (ParameterizedType) expectedType;
      if (!(parameterizedType.getRawType() instanceof Class && Set.class.isAssignableFrom((Class<?>) parameterizedType.getRawType()))) {
        throw new XL4JRuntimeException("expectedType is not a Set");
      }
      final Type[] typeArguments = parameterizedType.getActualTypeArguments();
      if (typeArguments.length == 1) {
        type = ConverterUtils.getBound(typeArguments[0]);
      } else {
        // will never get here
        throw new XL4JRuntimeException("Could not get two type argument from " + expectedType);
      }
    } else {
      throw new XL4JRuntimeException("expectedType not Class or ParameterizedType");
    }
    final XLArray xlArr = (XLArray) from;
    final XLValue[][] arr = xlArr.getArray();
    final Set<Object> targetSet = new LinkedHashSet<>();
    TypeConverter lastConverter = null;
    Class<?> lastClass = null;
    final TypeConverterRegistry typeConverterRegistry = _excel.getTypeConverterRegistry();
    final boolean isRow = arr.length == 1;
    final int n = arr.length == 1 ? arr[0].length : arr.length;
    for (int i = 0; i < n; i++) {
      final XLValue value = isRow ? arr[0][i] : arr[i][0];
      final Class<?> valueClass;
      final XLValue valueToConvert;
      if (value instanceof XLString && ((XLString) value).isXLObject()) {
        valueToConvert = ((XLString) value).toXLObject();
        valueClass = valueToConvert.getClass();
      } else {
        valueClass = value.getClass();
        valueToConvert = value;
      }
      if (lastConverter == null || !valueClass.equals(lastClass)) {
        lastClass = valueClass;
        lastConverter = typeConverterRegistry.findConverter(ExcelToJavaTypeMapping.of(lastClass, type));
        if (lastConverter == null) {
          // TODO should we use conversion to Object here?
          throw new XL4JRuntimeException("Could not find type converter for " + lastClass + " using component type " + type);
        }
      }
      targetSet.add(lastConverter.toJavaObject(type, valueToConvert));
    }
    return targetSet;
  }

}