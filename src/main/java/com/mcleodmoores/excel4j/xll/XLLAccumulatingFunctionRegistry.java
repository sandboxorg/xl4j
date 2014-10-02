/**
 * Copyright (C) 2014-Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.excel4j.xll;

import java.util.ArrayList;
import java.util.List;

import com.mcleodmoores.excel4j.lowlevel.LowLevelExcelCallback;

/**
 * 
 */
public class XLLAccumulatingFunctionRegistry implements LowLevelExcelCallback {
  /**
   * Native accessed data structure (public fields for speed).
   */
  public static class LowLevelEntry {
    // CHECKSTYLE:OFF
    public String _dllPath;
    public String _functionExportName;
    public String _functionSignature;
    public String _functionWorksheetName;
    public String _argumentNames;
    public int _functionType;
    public String _functionCategory;
    public String _acceleratorKey;
    public String _helpTopic;
    public String _description;
    public String[] _argsHelp;
    // CHECKSTYLE:ON
  }
  
  private List<LowLevelEntry> _entries = new ArrayList<>();
  
  @Override
  // CHECKSTYLE:OFF can't control signature.
  public int xlfRegister(final String dllPath, final String functionExportName, final String functionSignature, 
      final String functionWorksheetName, final String argumentNames, final int functionType, 
      final String functionCategory, final String acceleratorKey, final String helpTopic, 
      final String description, final String... argsHelp) {
    LowLevelEntry entry = new LowLevelEntry();
    entry._dllPath = dllPath;
    entry._functionExportName = functionExportName;
    entry._functionSignature = functionSignature;
    entry._functionWorksheetName = functionWorksheetName;
    entry._argumentNames = argumentNames;
    entry._functionType = functionType;
    entry._functionCategory = functionCategory;
    entry._acceleratorKey = acceleratorKey;
    entry._helpTopic = helpTopic;
    entry._description = description;
    entry._argsHelp = argsHelp;
    _entries.add(entry);
    return 0;
  }

  public LowLevelEntry[] getEntries() {
    return _entries.toArray(new LowLevelEntry[] {});
  }
}
