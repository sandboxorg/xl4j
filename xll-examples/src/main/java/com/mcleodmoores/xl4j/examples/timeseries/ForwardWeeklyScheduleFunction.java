/**
 * Copyright (C) 2017 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.examples.timeseries;

import java.util.function.BiFunction;

import org.threeten.bp.LocalDate;

import com.mcleodmoores.xl4j.v1.api.annotations.TypeConversionMode;
import com.mcleodmoores.xl4j.v1.api.annotations.XLFunctions;
import com.mcleodmoores.xl4j.v1.api.annotations.XLNamespace;
import com.mcleodmoores.xl4j.v1.util.ArgumentChecker;

/**
 * Generates a weekly schedule from the start date to end date inclusive.
 */
@XLNamespace("Schedule.")
@XLFunctions(
    prefix = "ForwardWeekly",
    typeConversionMode = TypeConversionMode.OBJECT_RESULT,
    description = "Generates a weekly schedule from the start to end date",
    category = "Schedule")
public class ForwardWeeklyScheduleFunction implements BiFunction<LocalDate, LocalDate, Schedule> {

  @Override
  public Schedule apply(final LocalDate start, final LocalDate endInclusive) {
    ArgumentChecker.notNull(start, "start");
    ArgumentChecker.notNull(endInclusive, "endInclusive");
    LocalDate date = start;
    final Schedule result = new Schedule();
    while (!date.isAfter(endInclusive)) {
      result.add(date);
      date = date.plusDays(7);
    }
    return result;
  }

}
