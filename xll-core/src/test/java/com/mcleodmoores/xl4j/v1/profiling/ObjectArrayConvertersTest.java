/**
 *
 */
package com.mcleodmoores.xl4j.v1.profiling;

import org.testng.annotations.Test;

import com.mcleodmoores.xl4j.v1.api.core.ExcelFactory;
import com.mcleodmoores.xl4j.v1.typeconvert.converters.ObjectArrayXLArrayTypeConverter;
import com.mcleodmoores.xl4j.v1.typeconvert.converters.ObjectArrayXLArrayTypeConverter2;

/**
 *
 */
public class ObjectArrayConvertersTest {

  /**
   * Tests the time taken to convert an array of Object.
   */
  @Test
  public void testConvertToMostSpecificXlType() {
    final ObjectArrayXLArrayTypeConverter converter = new ObjectArrayXLArrayTypeConverter(ExcelFactory.getInstance());
    final long hotspotWarmup = 10;
    final long testRuns = 100;
    final Object[] toConvert = new Object[] {Boolean.FALSE, 1, 1.5d};
    for (long i = 0; i < hotspotWarmup; i++) {
      converter.toXLValue(toConvert);
    }
    final long startTime = System.nanoTime();
    for (long i = 0; i < testRuns; i++) {
      converter.toXLValue(toConvert);
    }
    final long endTime = System.nanoTime();
    System.err.println("testConvertToMostSpecificXlType: " + (endTime - startTime) / 1000000 + "ms");
  }

  /**
   * Tests the time taken to convert an array of Object.
   */
  @Test
  public void testConvertToXlObject() {
    final ObjectArrayXLArrayTypeConverter2 converter = new ObjectArrayXLArrayTypeConverter2(ExcelFactory.getInstance());
    final long hotspotWarmup = 10;
    final long testRuns = 100;
    final Object[] toConvert = new Object[] {Boolean.FALSE, 1, 1.5d};
    for (long i = 0; i < hotspotWarmup; i++) {
      converter.toXLValue(toConvert);
    }
    final long startTime = System.nanoTime();
    for (long i = 0; i < testRuns; i++) {
      converter.toXLValue(toConvert);
    }
    final long endTime = System.nanoTime();
    System.err.println("testConvertToXlObject: " + (endTime - startTime) / 1000000 + "ms");
  }
}
