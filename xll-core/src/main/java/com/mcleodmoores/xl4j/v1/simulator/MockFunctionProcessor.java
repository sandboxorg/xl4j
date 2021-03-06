/**
 * Copyright (C) 2014 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.v1.simulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mcleodmoores.xl4j.v1.api.core.Excel;
import com.mcleodmoores.xl4j.v1.api.core.ExcelFactory;
import com.mcleodmoores.xl4j.v1.api.core.ExcelFunctionCallHandler;
import com.mcleodmoores.xl4j.v1.api.values.XLError;
import com.mcleodmoores.xl4j.v1.api.values.XLValue;
import com.mcleodmoores.xl4j.v1.util.XL4JRuntimeException;
import com.mcleodmoores.xl4j.v1.xll.NativeExcelFunctionEntryAccumulator;
import com.mcleodmoores.xl4j.v1.xll.NativeExcelFunctionEntryAccumulator.FunctionEntry;

/**
 * A mock function processor that allows Excel functions to be tested in Java.
 */
public final class MockFunctionProcessor {
  /** The logger */
  private static final Logger LOGGER = LoggerFactory.getLogger(MockFunctionProcessor.class);
  /** A static instance */
  private static final MockFunctionProcessor INSTANCE = new MockFunctionProcessor();

  /**
   * Gets an instance.
   *
   * @return an instance
   */
  public static MockFunctionProcessor getInstance() {
    return INSTANCE;
  }

  private final Excel _excel = ExcelFactory.getInstance();
  private final FunctionEntry[] _entries;
  private final ExcelFunctionCallHandler _excelCallHandler;

  /**
   * Create a function processor.
   */
  private MockFunctionProcessor() {
    _excel.getFunctionRegistry().registerFunctions(_excel.getExcelCallback());
    final NativeExcelFunctionEntryAccumulator excelFunctionRegistry = (NativeExcelFunctionEntryAccumulator) _excel.getExcelCallback()
        .getLowLevelExcelCallback();
    _entries = excelFunctionRegistry.getEntries();
    _excelCallHandler = _excel.getExcelCallHandler();
  }

  /**
   * Invoke a function.
   *
   * @param functionName
   *          the name of the function
   * @param args
   *          the arguments
   * @return the result
   */
  public XLValue invoke(final String functionName, final XLValue... args) {
    final List<Integer> exportNumbers = new ArrayList<>();
    // might have more than one function called the same thing
    for (final FunctionEntry entry : _entries) {
      if (entry._functionWorksheetName.equals(functionName)) {
        exportNumbers.add(entry._exportNumber);
      }
    }
    if (exportNumbers.isEmpty()) {
      throw new XL4JRuntimeException("Could not find function called " + functionName);
    }
    final Object[] newArgs = new Object[args.length];
    System.arraycopy(args, 0, newArgs, 0, args.length);
    if (exportNumbers.size() == 1) { // old behaviour
      try {
        return _excelCallHandler.invoke(exportNumbers.get(0), args);
      } catch (IllegalArgumentException | ClassCastException e) {
        throw new XL4JRuntimeException("Problem invoking function " + functionName + " with args " + Arrays.toString(newArgs), e);
      }
    }
    XLValue lastError = null;
    for (final int exportNumber : exportNumbers) {
      try {
        final XLValue result = _excelCallHandler.invoke(exportNumber, args);
        if (result instanceof XLError) {
          lastError = result;
          LOGGER.info("Error returned by function {} with args {}", functionName, Arrays.toString(newArgs));
          continue;
        }
        return result;
      } catch (final IllegalArgumentException | ClassCastException e) {
        LOGGER.error("Problem invoking function {} with args {}: {}", functionName, Arrays.toString(newArgs), e.getMessage());
        // try the next one
      }
    }
    if (lastError == null) {
      throw new XL4JRuntimeException("Problem invoking function " + functionName + " with args " + Arrays.toString(newArgs));
    }
    // horrible, but what else can be done?
    return lastError;
  }

}