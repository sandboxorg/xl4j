/**
 * Copyright (C) 2014-Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.v1.typeconvert.converters;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.mcleodmoores.xl4j.v1.api.typeconvert.AbstractTypeConverter;
import com.mcleodmoores.xl4j.v1.api.typeconvert.ExcelToJavaTypeMapping;
import com.mcleodmoores.xl4j.v1.api.typeconvert.JavaToExcelTypeMapping;
import com.mcleodmoores.xl4j.v1.api.values.XLNumber;
import com.mcleodmoores.xl4j.v1.api.values.XLString;
import com.mcleodmoores.xl4j.v1.api.values.XLValue;
import com.mcleodmoores.xl4j.v1.util.XL4JRuntimeException;

/**
 * Unit tests for {@link EnumXLStringTypeConverter}.
 */
@Test
public class EnumXLStringTypeConverterTest {
  /** The expected priority */
  private static final int EXPECTED_PRIORITY = 7;
  /** A double */
  private static final double TEN_D = 10d;
  /** XLString. */
  private static final XLString XL_STRING = XLString.of("TEST");

  /** Enum. */
  enum TestEnum {
    /** Test value. */
    TEST
  };

  /** The converter */
  private static final AbstractTypeConverter CONVERTER = new EnumXLStringTypeConverter();

  /**
   * Tests that the java type is {@link TestEnum}.
   */
  @Test
  public void testGetExcelToJavaTypeMapping() {
    assertEquals(CONVERTER.getExcelToJavaTypeMapping(), ExcelToJavaTypeMapping.of(XLString.class, Enum.class));
  }

  /**
   * Tests that the excel type is {@link XLString}.
   */
  @Test
  public void testGetJavaToExcelTypeMapping() {
    assertEquals(CONVERTER.getJavaToExcelTypeMapping(), JavaToExcelTypeMapping.of(Enum.class, XLString.class));
  }

  /**
   * Tests the expected priority.
   */
  @Test
  public void testPriority() {
    assertEquals(CONVERTER.getPriority(), EXPECTED_PRIORITY);
  }

  /**
   * Tests that passing in a null object gives the expected exception.
   */
  @Test(expectedExceptions = XL4JRuntimeException.class)
  public void testNullObject() {
    CONVERTER.toXLValue(null);
  }

  /**
   * Tests that passing in a null expected Java class gives the expected exception.
   */
  @Test(expectedExceptions = XL4JRuntimeException.class)
  public void testNullExpectedClass() {
    CONVERTER.toJavaObject(null, XL_STRING);
  }

  /**
   * Tests that passing in a null object gives the expected exception.
   */
  @Test(expectedExceptions = XL4JRuntimeException.class)
  public void testNullXLValue() {
    CONVERTER.toJavaObject(TestEnum.class, null);
  }

  /**
   * Tests for the exception when the object to convert is the wrong type.
   */
  @Test(expectedExceptions = ClassCastException.class)
  public void testWrongTypeToJavaConversion() {
    CONVERTER.toJavaObject(TestEnum.class, XLNumber.of(TEN_D));
  }

  /**
   * Tests for the exception when the expected class is wrong.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testWrongExpectedClassToJavaConversion() {
    CONVERTER.toJavaObject(String.class, XL_STRING);
  }

  /**
   * Tests for the exception when {@link XLValue} to convert is the wrong type.
   */
  @Test(expectedExceptions = ClassCastException.class)
  public void testWrongTypeToXLConversion() {
    CONVERTER.toXLValue(TEN_D);
  }

  /**
   * Tests that the expected type is ignored during conversion to a XL class.
   */
  @Test
  public void testWrongExpectedClassToXLConversion() {
    CONVERTER.toXLValue(TestEnum.TEST);
  }

  /**
   * Tests the conversion from a {@link TestEnum}.
   */
  @Test
  public void testConversionFromString() {
    final XLValue converted = (XLValue) CONVERTER.toXLValue(TestEnum.TEST);
    assertTrue(converted instanceof XLString);
    final XLString xlString = (XLString) converted;
    assertEquals(xlString.getValue(), TestEnum.TEST.name());
  }

  /**
   * Tests the conversion from a {@link XLString}.
   */
  @Test
  public void testConversionFromXLString() {
    final Object converted = CONVERTER.toJavaObject(TestEnum.class, XL_STRING);
    assertTrue(converted instanceof TestEnum);
    final TestEnum en = (TestEnum) converted;
    assertEquals(en, TestEnum.TEST);
  }

}
