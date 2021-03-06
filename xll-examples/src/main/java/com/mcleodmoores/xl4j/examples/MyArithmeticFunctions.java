/**
 * Copyright (C) 2016 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.examples;

import com.mcleodmoores.xl4j.v1.api.annotations.TypeConversionMode;
import com.mcleodmoores.xl4j.v1.api.annotations.XLFunction;
import com.mcleodmoores.xl4j.v1.api.annotations.XLParameter;
import com.mcleodmoores.xl4j.v1.api.values.XLNumber;
import com.mcleodmoores.xl4j.v1.util.ArgumentChecker;

/**
 * Simple example functions.
 */
public final class MyArithmeticFunctions {

  /**
   * Restricted constructor.
   */
  private MyArithmeticFunctions() {
  }

  /**
   * Adds two numbers.
   *
   * @param one
   *          the first number, not null
   * @param two
   *          the second number, not null
   * @return the sum of the two numbers
   */
  @XLFunction(name = "MyAdd",
              description = "Add 2 numbers",
              category = "Mine", typeConversionMode = TypeConversionMode.SIMPLEST_RESULT)
  public static XLNumber myadd(@XLParameter(name = "num 1", description = "The first number") final XLNumber one,
      @XLParameter(name = "num 2", description = "The second number") final XLNumber two) {
    ArgumentChecker.notNull(one, "one");
    ArgumentChecker.notNull(two, "two");
    return XLNumber.of(one.getValue() + two.getValue());
  }

  /**
   * Subtracts two numbers.
   *
   * @param one
   *          the first number, not null
   * @param two
   *          the second number, not null
   * @return the difference between the two numbers
   */
  @XLFunction(name = "MySubtract",
              description = "Subtract 2 numbers",
              category = "Mine",
              typeConversionMode = TypeConversionMode.SIMPLEST_RESULT)
  public static XLNumber mysubtract(@XLParameter(name = "num 1", description = "The first number") final XLNumber one,
      @XLParameter(name = "num 2", description = "The second number") final XLNumber two) {
    ArgumentChecker.notNull(one, "one");
    ArgumentChecker.notNull(two, "two");
    return XLNumber.of(one.getValue() - two.getValue());
  }

  /**
   * Multiplies two numbers.
   *
   * @param one
   *          the first number, not null
   * @param two
   *          the second number, not null
   * @return the multiple of the two numbers
   */
  @XLFunction(name = "MyMultiply",
              description = "Multiply 2 numbers",
              category = "Mine",
              typeConversionMode = TypeConversionMode.SIMPLEST_RESULT)
  public static XLNumber mymultiply(@XLParameter(name = "num 1", description = "The first number") final XLNumber one,
      @XLParameter(name = "num 2", description = "The second number") final XLNumber two) {
    ArgumentChecker.notNull(one, "one");
    ArgumentChecker.notNull(two, "two");
    return XLNumber.of(one.getValue() * two.getValue());
  }

  /**
   * Divides two numbers.
   *
   * @param one
   *          the first number, not null
   * @param two
   *          the second number, not null
   * @return the quotient of the two numbers
   */
  @XLFunction(name = "MyDivide",
              description = "Divide 2 numbers",
              category = "Mine",
              typeConversionMode = TypeConversionMode.SIMPLEST_RESULT)
  public static XLNumber mydivide(@XLParameter(name = "num 1", description = "The first number") final XLNumber one,
      @XLParameter(name = "num 2", description = "The second number") final XLNumber two) {
    ArgumentChecker.notNull(one, "one");
    ArgumentChecker.notNull(two, "two");
    return XLNumber.of(one.getValue() / two.getValue());
  }

  /**
   * Recursively calculates the factorial of a number.
   *
   * @param number
   *          the number, not null
   * @return the factorial
   */
  @XLFunction(name = "MyFactorial",
              description = "Factorial",
              category = "Mine",
              typeConversionMode = TypeConversionMode.SIMPLEST_RESULT)
  public static XLNumber myfactorial(@XLParameter(name = "num", description = "The number") final XLNumber number) {
    if ((long) number.getValue() == 1) {
      return XLNumber.of(1);
    }
    return mymultiply(number, myfactorial(mysubtract(number, XLNumber.of(1))));
  }
}
