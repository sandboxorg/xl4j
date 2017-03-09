/**
 * Copyright (C) 2014 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.typeconvert.converters;

import java.lang.reflect.Type;

import com.mcleodmoores.xl4j.Excel;
import com.mcleodmoores.xl4j.heap.Heap;
import com.mcleodmoores.xl4j.typeconvert.AbstractTypeConverter;
import com.mcleodmoores.xl4j.util.ArgumentChecker;
import com.mcleodmoores.xl4j.values.XLObject;

/**
 * Type converter for general objects into XLObject handles. Note the lower priority, which means all the other converters get a crack at
 * doing something nicer first.
 */
public class ObjectXLObjectTypeConverter extends AbstractTypeConverter {
  /** The priority */
  private static final int OBJECT_CONVERTER_PRIORITY = 5;
  /** Heap containing XLObjects */
  private final Heap _heap;

  /**
   * Default constructor.
   *
   * @param excel
   *          the excel object to allow heap access
   */
  public ObjectXLObjectTypeConverter(final Excel excel) {
    super(Object.class, XLObject.class, OBJECT_CONVERTER_PRIORITY);
    _heap = ArgumentChecker.notNull(excel, "excel").getHeap();
  }

  @Override
  public Object toXLValue(final Object from) {
    ArgumentChecker.notNull(from, "from");
    return XLObject.of(from.getClass().getSimpleName(), _heap.getHandle(from));
  }

  @Override
  public Object toJavaObject(final Type expectedType, final Object from) {
    ArgumentChecker.notNull(from, "from");
    final XLObject xlObj = (XLObject) from;
    return _heap.getObject(xlObj.getHandle());
  }

}
