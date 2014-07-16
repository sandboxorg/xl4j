/**
 * Copyright (C) 2014-Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.excel4j.typeconvert;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import javassist.Modifier;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mcleodmoores.excel4j.util.Excel4JRuntimeException;

/**
 * Type resolver.
 */
public class TypeConverterRegistry {
  private static Logger s_logger = LoggerFactory.getLogger(TypeConverterRegistry.class);
  
  // we want highest priority keys first, so we use a reversing comparator.
  private ConcurrentSkipListMap<Integer, List<TypeConverter>> _converters = new ConcurrentSkipListMap<>(Collections.reverseOrder());
  
  /**
   * Construct a TypeResolver.
   */
  public TypeConverterRegistry() {
    scanAndCreateTypeConverters();
  }
  
  @SuppressWarnings("rawtypes")
  private void scanAndCreateTypeConverters() {
    Reflections reflections = new Reflections();
    Set<Class<? extends TypeConverter>> typeConverterClasses = reflections.getSubTypesOf(TypeConverter.class);
    for (Class<? extends TypeConverter> typeConverterClass : typeConverterClasses) {
      if (Modifier.isAbstract(typeConverterClass.getModifiers())) {
        continue; // skip over abstract type converters.
      }
      Constructor constructor;
      try {
        constructor = typeConverterClass.getConstructor((Class<?>[]) null);
        TypeConverter typeConverter = (TypeConverter) constructor.newInstance((Object[]) null);
        int priority = typeConverter.getPriority();
        if (!_converters.containsKey(priority)) {
          _converters.putIfAbsent(priority, new ArrayList<TypeConverter>());
        }
        _converters.get(typeConverter.getPriority()).add(typeConverter);
      } catch (InstantiationException e) {
        s_logger.error("Could not find no args constructor on TypeConverter {}", typeConverterClass, e);
        throw new Excel4JRuntimeException("Could not find static getInstance() method on TypeConverter (see log)", e);
      } catch (SecurityException e) {
        s_logger.error("Security Exception while trying to create instance of TypeConverter {}", typeConverterClass, e);
        throw new Excel4JRuntimeException("Security Exception while trying to create instance of TypeConverter (see log)", e);
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        s_logger.error("Unexpected Exception while trying to create instance of TypeConverter {}", typeConverterClass, e);
        throw new Excel4JRuntimeException("Unexpected Exception while trying to create instance of TypeConverter (see log)", e);
      } catch (NoSuchMethodException e) {
        s_logger.error("Could not find constructor method on TypeConverter {}", typeConverterClass, e);
      }
    }
  }
  
  /**
   * Find a type converter to perform the required conversion, searching linearly in priority order
   * and returning the first match.
   * @param requiredMapping the required conversion
   * @return a type converter to perform the conversion
   */
  public TypeConverter findConverter(final ExcelToJavaTypeMapping requiredMapping) {
    for (int priority : _converters.keySet()) {
      List<TypeConverter> converters = _converters.get(priority);
      for (TypeConverter typeConverter : converters) {
        if (requiredMapping.isAssignableFrom(typeConverter.getExcelToJavaTypeMapping())) {
          return (TypeConverter) typeConverter;
        }
      }
    }
    return null;
  }
  
  /**
   * Find a type converter to perform the required conversion, searching linearly in priority order.
   * This method is used to find a converter from Java back into Excel, when you don't know the target Excel type.
   * and returning the first match.
   * @param requiredJava the Java type required to convert from.
   * @return a type converter to perform the conversion
   */
  public TypeConverter findConverter(final Type requiredJava) {
    for (int priority : _converters.keySet()) {
      List<TypeConverter> converters = _converters.get(priority);
      for (TypeConverter typeConverter : converters) {
        if (requiredJava.getClass().isAssignableFrom(typeConverter.getJavaToExcelTypeMapping().getJavaType().getClass())) {
          return (TypeConverter) typeConverter;
        }
      }
    }
    return null;
  }
  

}
