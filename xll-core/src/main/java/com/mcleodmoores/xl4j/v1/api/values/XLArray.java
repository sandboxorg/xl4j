/**
 * Copyright (C) 2014 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.v1.api.values;

import java.util.Arrays;

import com.mcleodmoores.xl4j.v1.util.ArgumentChecker;

/**
 * Java representation of the xloper type xltypeMulti It can take the form of a two dimensional array of mixed types of xlopers.
 */
public final class XLArray implements XLReference {
  /** The underlying array */
  private final XLValue[][] _array;
  /** True if the array is a row */
  private final boolean _isRow;
  /** True if the array is a column */
  private final boolean _isColumn;
  /** True if the array is an area */
  private final boolean _isArea;

  /**
   * @param valueRange
   *          the value range
   */
  private XLArray(final XLValue[][] valueRange) {
    _array = valueRange;
    boolean isRow;
    boolean isColumn;
    boolean isArea;
    if (valueRange.length == 1) {
      isRow = true;
      isColumn = false;
      isArea = false;
    } else {
      isRow = false;
      isColumn = true;
      for (final XLValue[] array : valueRange) {
        if (array.length > 1) {
          isColumn = false;
          break;
        }
      }
      isArea = !isColumn;
    }
    _isRow = isRow;
    _isColumn = isColumn;
    _isArea = isArea;
  }

  /**
   * Static factory method to create an instance of XLArray.
   *
   * @param array
   *          a two dimensional array containing XLValues
   * @return an instance
   */
  public static XLArray of(final XLValue[][] array) {
    ArgumentChecker.notNullOrEmpty(array, "array"); // not checking for squareness.
    return new XLArray(array);
  }

  /**
   * @return a two dimensional array of values, not null
   */
  public XLValue[][] getArray() {
    return _array;
  }

  /**
   * Returns true if this array represents a row.
   *
   * @return true if the array is a row
   */
  public boolean isRow() {
    return _isRow;
  }

  /**
   * Returns true if this array represents a column.
   *
   * @return true if the array is a column
   */
  public boolean isColumn() {
    return _isColumn;
  }

  /**
   * Returns true if this array is neither a row nor column.
   *
   * @return true if the array is an area
   */
  public boolean isArea() {
    return _isArea;
  }

  @Override
  public <E> E accept(final XLValueVisitor<E> visitor) {
    return visitor.visitXLArray(this);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.deepHashCode(_array); // Arrays.hashCode() had issues.
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof XLArray)) {
      return false;
    }
    final XLArray other = (XLArray) obj;
    if (!Arrays.deepEquals(_array, other._array)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "XLArray[" + Arrays.deepToString(_array) + "]";
  }

}
