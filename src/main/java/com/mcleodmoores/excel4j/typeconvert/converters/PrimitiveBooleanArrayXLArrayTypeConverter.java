package com.mcleodmoores.excel4j.typeconvert.converters;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import com.mcleodmoores.excel4j.Excel;
import com.mcleodmoores.excel4j.typeconvert.AbstractTypeConverter;
import com.mcleodmoores.excel4j.typeconvert.ExcelToJavaTypeMapping;
import com.mcleodmoores.excel4j.typeconvert.TypeConverter;
import com.mcleodmoores.excel4j.typeconvert.TypeConverterRegistry;
import com.mcleodmoores.excel4j.util.ArgumentChecker;
import com.mcleodmoores.excel4j.util.Excel4JRuntimeException;
import com.mcleodmoores.excel4j.values.XLArray;
import com.mcleodmoores.excel4j.values.XLValue;

/**
 * Type converter to convert from arrays of booleans to Excel arrays and back again. The input
 * array from Excel can contain any type of {@link XLValue} (e.g. <code>XLBoolean</code>,
 * <code>XLString("true")</code>) and an attempt will be made to convert this value to a boolean.
 */
public final class PrimitiveBooleanArrayXLArrayTypeConverter extends AbstractTypeConverter {

  private final TypeConverterRegistry _typeConverterRegistry;

  /**
   * Default constructor.
   * @param excel  the excel context object, used to access the type converter registry.
   */
  public PrimitiveBooleanArrayXLArrayTypeConverter(final Excel excel) {
    super(boolean[].class, XLArray.class);
    ArgumentChecker.notNull(excel, "excel");
    _typeConverterRegistry = excel.getTypeConverterRegistry();
  }

  @Override
  public Object toXLValue(final Type expectedType, final Object from) {
    ArgumentChecker.notNull(from, "from");
    if (!from.getClass().isArray()) {
      throw new Excel4JRuntimeException("from parameter must be an array");
    }
    Type componentType = null;
    if (expectedType instanceof Class) {
      final Class<?> expectedClass = from.getClass();
      componentType = expectedClass.getComponentType();
      if (componentType == null) {
        throw new Excel4JRuntimeException("component type of \"from\" parameter is null");
      }
    } else {
      throw new Excel4JRuntimeException("expectedType not array or GenericArrayType: have " + expectedType);
    }
    System.err.println("ComponentType = " + componentType);
    final TypeConverter converter = _typeConverterRegistry.findConverter(componentType);
    final boolean[] fromArr = (boolean[]) from;
    final XLValue[][] toArr = new XLValue[1][fromArr.length];
    for (int i = 0; i < fromArr.length; i++) {
      final XLValue value = (XLValue) converter.toXLValue(componentType, fromArr[i]);
      toArr[0][i] = value;
    }
    return XLArray.of(toArr);
  }

  @Override
  public Object toJavaObject(final Type expectedType, final Object from) {
    ArgumentChecker.notNull(from, "from");
    final XLArray xlArr = (XLArray) from;
    Type componentType = null;
    if (expectedType instanceof Class) {
      final Class<?> expectedClass = (Class<?>) expectedType;
      componentType = expectedClass.getComponentType();
    } else if (expectedType instanceof GenericArrayType) {
      final GenericArrayType genericArrayType = (GenericArrayType) expectedType;
      componentType = genericArrayType.getGenericComponentType();
    } else {
      throw new Excel4JRuntimeException("expectedType not array or GenericArrayType: have" + expectedType);
    }

    final XLValue[][] arr = xlArr.getArray();
    TypeConverter lastConverter = null;
    Class<?> lastClass = null;
    if (arr.length == 1) { // array is a single row
      final boolean[] targetArr = new boolean[arr[0].length];
      for (int i = 0; i < arr[0].length; i++) {
        final XLValue val = arr[0][i];
        // This is a rather weak attempt at optimizing converter lookup - other options seemed to have greater overhead.
        if (lastConverter == null || (!val.getClass().equals(lastClass))) {
          lastClass = val.getClass();
          lastConverter = _typeConverterRegistry.findConverter(ExcelToJavaTypeMapping.of(lastClass, componentType));
        }
        if (lastConverter == null) {
          throw new Excel4JRuntimeException("Could not find type converter for " + lastClass + " using component type " + componentType);
        }
        targetArr[i] = (boolean) lastConverter.toJavaObject(componentType, val);
      }
      return targetArr;
    } else { // array is single column
      final boolean[] targetArr = new boolean[arr.length];
      for (int i = 0; i < arr.length; i++) {
        final XLValue val = arr[i][0];
        // This is a rather weak attempt at optimizing converter lookup - other options seemed to have greater overhead.
        if (lastConverter == null || (!val.getClass().equals(lastClass))) {
          lastClass = val.getClass();
          lastConverter = _typeConverterRegistry.findConverter(ExcelToJavaTypeMapping.of(lastClass, componentType));
        }
        if (lastConverter == null) {
          throw new Excel4JRuntimeException("Could not find type converter for " + lastClass + " using component type " + componentType);
        }
        targetArr[i] = (boolean) lastConverter.toJavaObject(componentType, val);
      }
      return targetArr;
    }

  }
}
