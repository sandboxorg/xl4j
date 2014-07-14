/**
 * Copyright (C) 2014-Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.excel4j.typeconvert;

import com.mcleodmoores.excel4j.values.XLValue;

/**
 * Interface for converting to and from XLValue types to Java types.
 * Java Generics system not powerful enough to be of any use here as most types are
 * determined only at run-time.
 */
public interface TypeConverter {
  /**
   * Get the Excel->Java mapping provided by this converter.
   * @return the supported mapping
   */
  ExcelToJavaTypeMapping getExcelToJavaTypeMapping();
  /**
   * Get the Java->Excel mapping provided by this converter.
   * @return the supported mapping
   */
  JavaToExcelTypeMapping getJavaToExcelTypeMapping();
  
  /**
   * Convert from the supported Java type to the supported Excel type.
   * @param from  the Java object to convert to an Excel type
   * @return an Excel conversion of a Java type
   */
  XLValue toXLValue(Object from);
  
  /**
   * Convert from the supported Excel type to the supported Java type.
   * @param from  the Excel object to convert into a Java type
   * @return a Java object converted from an Excel object
   */
  Object toJavaObject(XLValue from);
  
  /**
   * Get the priority level of this converter, higher values have higher priority.
   * @return the priority level, higher values being higher priority
   */
  int getPriority();
}