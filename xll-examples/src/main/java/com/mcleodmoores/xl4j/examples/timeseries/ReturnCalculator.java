/**
 * Copyright (C) 2016 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.examples.timeseries;

import java.util.function.Function;

import org.threeten.bp.LocalDate;

import com.mcleodmoores.xl4j.TypeConversionMode;
import com.mcleodmoores.xl4j.XLConstructor;
import com.mcleodmoores.xl4j.XLFunction;
import com.mcleodmoores.xl4j.util.ArgumentChecker;

/**
 * Calculates the returns of a time series, either assuming continuous compounding or at the frequency of the time series data.
 */
public class ReturnCalculator implements Function<TimeSeries, TimeSeries> {
  /** True if continuous returns are required */
  private final boolean _continuous;

  /**
   * Creates an instance.
   *
   * @param continuous
   *          true if continuous returns are required
   */
  @XLConstructor(name = "TimeSeriesReturn", description = "Calculates returns of a time series", category = "Time series")
  public ReturnCalculator(final boolean continuous) {
    _continuous = continuous;
  }

  @XLFunction(name = "Return",
              description = "Calculates returns of a time series",
              category = "Time series",
              typeConversionMode = TypeConversionMode.SIMPLEST_RESULT)
  @Override
  public TimeSeries apply(final TimeSeries ts) {
    ArgumentChecker.notNull(ts, "ts");
    ArgumentChecker.notNullArray(ts.getValues(), "ts.values");
    final int n = ts.size();
    final LocalDate[] dates = new LocalDate[n - 1];
    final Double[] returns = new Double[n - 1];
    for (int i = 0; i < n - 1; i++) {
      dates[i] = ts.getDate(i + 1);
      returns[i] = _continuous ? Math.log(ts.getValue(i + 1) / ts.getValue(i)) : ts.getValue(i + 1) / ts.getValue(i) - 1;
    }
    return TimeSeries.of(dates, returns);
  }
}