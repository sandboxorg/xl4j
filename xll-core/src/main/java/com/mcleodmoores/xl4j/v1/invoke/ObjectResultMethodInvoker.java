/**
 * Copyright (C) 2014 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.v1.invoke;

import java.lang.reflect.Method;

import com.mcleodmoores.xl4j.v1.api.typeconvert.TypeConverter;
import com.mcleodmoores.xl4j.v1.api.values.XLObject;
import com.mcleodmoores.xl4j.v1.api.values.XLValue;
import com.mcleodmoores.xl4j.v1.util.ArgumentChecker;

/**
 * A class holding the converters required to convert arguments into the appropriate types and convert the result.
 */
public class ObjectResultMethodInvoker extends AbstractMethodInvoker {
  private final TypeConverter _objectXlObjectConverter;

  /**
   * Constructor.
   *
   * @param method
   *          the method to call.
   * @param argumentConverters
   *          the converters required to call the method
   * @param returnConverter
   *          the converter required to convert the result back to an Excel type
   * @param objectXlObjectConverter
   *          a converter to convert the object into an object if necessary
   */
  public ObjectResultMethodInvoker(final Method method, final TypeConverter[] argumentConverters, final TypeConverter returnConverter,
      final TypeConverter objectXlObjectConverter) {
    super(method, argumentConverters, returnConverter, objectXlObjectConverter);
    _objectXlObjectConverter = ArgumentChecker.notNull(objectXlObjectConverter, "objectXlObjectConverter");
  }

  @Override
  protected XLValue convertResult(final Object object, final TypeConverter returnConverter) {
    if (object instanceof XLObject) {
      // if multiple layers of method invoker, don't put already encoded XLObjects on heap.
      // this happens in case of JMethod/JConstruct etc.
      return (XLValue) object;
    }
    return (XLValue) _objectXlObjectConverter.toXLValue(object);
  }

}
