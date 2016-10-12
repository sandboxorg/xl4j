package com.mcleodmoores.excel4j.typeconvert.converters;

import java.lang.reflect.Type;

import com.mcleodmoores.excel4j.typeconvert.AbstractTypeConverter;
import com.mcleodmoores.excel4j.util.ArgumentChecker;
import com.mcleodmoores.excel4j.values.XLError;
import com.mcleodmoores.excel4j.values.XLNumber;

/**
 * Type converter to convert from Doubles to Excel Numbers and back again.
 */
public final class DoubleXLNumberTypeConverter extends AbstractTypeConverter {
  /**
   * Default constructor.
   */
  public DoubleXLNumberTypeConverter() {
    super(Double.class, XLNumber.class);
  }

  @Override
  public Object toXLValue(final Type expectedType, final Object from) {
    ArgumentChecker.notNull(from, "from");
    final Double d = (Double) from;
    if (d.isInfinite()) {
      return XLError.Div0;
    }
    if (d.isNaN()) {
      return XLError.NA;
    }
    return XLNumber.of(d);
  }

  @Override
  public Object toJavaObject(final Type expectedType, final Object from) {
    ArgumentChecker.notNull(from, "from");
    return (double) ((XLNumber) from).getValue();
  }
}
