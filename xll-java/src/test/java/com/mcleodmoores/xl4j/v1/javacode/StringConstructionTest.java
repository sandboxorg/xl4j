/**
 *
 */
package com.mcleodmoores.xl4j.v1.javacode;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import com.mcleodmoores.xl4j.v1.api.values.XLNumber;
import com.mcleodmoores.xl4j.v1.api.values.XLObject;
import com.mcleodmoores.xl4j.v1.api.values.XLString;
import com.mcleodmoores.xl4j.v1.api.values.XLValue;

/**
 * Tests construction of Strings from the function processor.
 */
public class StringConstructionTest extends TypeConstructionTests {

  /**
   * Tests construction of a String using new String().
   */
  @Test
  public void testJConstruct() {
    final XLValue xlValue = PROCESSOR.invoke("JConstruct", XLString.of("java.lang.String"));
    assertTrue(xlValue instanceof XLObject);
    final Object object = HEAP.getObject(((XLObject) xlValue).getHandle());
    assertTrue(object instanceof String);
    final String string = (String) object;
    assertTrue(string.isEmpty());
  }

  /**
   * Tests construction of a String using String.valueOf(double).
   */
  @Test
  public void testJMethod() {
    final XLValue xlValue = PROCESSOR.invoke("JStaticMethodX", XLString.of("java.lang.String"), XLString.of("valueOf"), XLNumber.of(10));
    assertTrue(xlValue instanceof XLObject);
    final Object object = HEAP.getObject(((XLObject) xlValue).getHandle());
    assertTrue(object instanceof String);
    final String string = (String) object;
    // double compare to avoid "10.0" != "10"
    assertEquals(Double.valueOf(string), 10.);
  }
}
