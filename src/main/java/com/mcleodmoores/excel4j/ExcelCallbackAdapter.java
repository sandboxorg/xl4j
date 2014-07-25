/**
 * Copyright (C) 2014-Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.excel4j;

import java.io.File;

import com.mcleodmoores.excel4j.javacode.MethodInvoker;
import com.mcleodmoores.excel4j.values.XLInteger;
import com.mcleodmoores.excel4j.values.XLLocalReference;
import com.mcleodmoores.excel4j.values.XLMultiReference;
import com.mcleodmoores.excel4j.values.XLNil;
import com.mcleodmoores.excel4j.values.XLString;
import com.mcleodmoores.excel4j.values.XLValue;
import com.mcleodmoores.excel4j.util.Excel4JRuntimeException;

/**
 * Provides a layer to process function metadata into relatively raw calls back to Excel.
 */
public class ExcelCallbackAdapter implements ExcelCallback {
  private File _dllPath;
  private RawExcelCallback _rawCallback;

  /**
   * Create a callback adapter.
   * @param dllPath  the path of the DLL implementing this XLL
   * @param rawCallback  the raw callback interface to call through to
   */
  public ExcelCallbackAdapter(final File dllPath, final RawExcelCallback rawCallback) {
    _dllPath = dllPath;
    _rawCallback = rawCallback;
  }
  
  @Override
  public void registerFunction(final FunctionDefinition functionDefinition) {
    FunctionMetadata functionMetadata = functionDefinition.getFunctionMetadata();
    MethodInvoker methodInvoker = functionDefinition.getMethodInvoker();
    XLNamespace namespaceAnnotation = functionMetadata.getNamespace();
    XLFunction functionAnnotation = functionMetadata.getFunctionSpec();
    XLArgument[] argumentAnnotations = functionMetadata.getArguments();
    
    final XLString functionName = buildFunctionName(methodInvoker, namespaceAnnotation, functionAnnotation);
    final XLString argumentNames = buildArgNames(argumentAnnotations);
    final XLInteger functionTypeInt = getFunctionType(functionAnnotation);
    final XLString signature = buildFunctionSignature(functionAnnotation, argumentAnnotations, methodInvoker);
    final XLValue functionCategory = buildFunctionCategory(functionAnnotation, methodInvoker);
    final XLValue helpTopic = buildHelpTopic(functionAnnotation);
    final XLValue description = buildDescription(functionAnnotation);
  }
  
  private XLValue buildDescription(final XLFunction functionAnnotation) {
    if (functionAnnotation != null && functionAnnotation.description() != null) {
      return XLString.of(functionAnnotation.description());
    } else {
      return XLNil.INSTANCE;
    }
  }
  
  private XLValue buildHelpTopic(final XLFunction functionAnnotation) {
    if (functionAnnotation != null && functionAnnotation.helpTopic() != null) {
      return XLString.of(functionAnnotation.helpTopic());
    } else {
      return XLNil.INSTANCE;
    }
  }
  private XLValue buildFunctionCategory(final XLFunction functionAnnotation, final MethodInvoker methodInvoker) {
    if (functionAnnotation != null && functionAnnotation.category() != null) {
      return XLString.of(functionAnnotation.category());
    } else {
      return XLString.of(methodInvoker.getMethodDeclaringClass().getSimpleName());
    }    
  }
  
  private XLString buildFunctionSignature(final XLFunction functionAnnotation, final XLArgument[] argumentAnnotations, final MethodInvoker methodInvoker) {
    StringBuilder signature = new StringBuilder();
    Class<? extends XLValue> excelReturnType = methodInvoker.getExcelReturnType();
    Class<? extends XLValue>[] parameterTypes = methodInvoker.getExcelParameterTypes();
    boolean isVolatile = (functionAnnotation != null) ? functionAnnotation.isVolatile() : false; // default
    boolean isMTSafe = (functionAnnotation != null) ? functionAnnotation.isMultiThreadSafe() : true; // default, this is the 2010s, yo.
    boolean isMacroEquivalent = (functionAnnotation != null) ? functionAnnotation.isMacroEquivalent() : false; // default
    boolean isAsynchronous = (functionAnnotation != null) ? functionAnnotation.isAsynchronous() : false; // default
    XLFunctionType functionType = (functionAnnotation != null) ? functionAnnotation.functionType() : XLFunctionType.FUNCTION; // default;
    if ((isVolatile && isMTSafe) || (isMTSafe && isMacroEquivalent)) {
      throw new Excel4JRuntimeException("Illegal combination of XLFunction attributes, cannot be volatile & thread-safe or macro-equivalent & thread-safe");
    }
    // Return type character
    if (functionType == XLFunctionType.COMMAND) {
      if (!excelReturnType.isAssignableFrom(XLInteger.class)) {
        throw new Excel4JRuntimeException("Commands must have a return type XLInteger (gets convertered to type J (int))");
      }
      signature.append("J"); // means int, but we'll convert from XLInteger to make the class hierarchy cleaner.
    } else {
      if (isAsynchronous) {
        signature.append(">X"); // means void function first parameter is asynchronous callback handle, which we don't expose to the user.
      } else {
        if (excelReturnType.isAssignableFrom(XLLocalReference.class)
            || excelReturnType.isAssignableFrom(XLMultiReference.class)) {
          // REVIEW: Not sure if this is a valid thing to do.
          signature.append("U"); // XLOPER12 range/ref/array. I've not idea if this is even valid. Not clear in docs.
        } else {
          signature.append("Q"); // XLOPER12 
        }
      }
    }
    // Parameters
    for (int i = 0; i < parameterTypes.length; i++) {
      XLArgument argumentAnnotation = argumentAnnotations[i];
      if (argumentAnnotation != null && argumentAnnotation.referenceType()) {
        if (!isMacroEquivalent) {
          throw new Excel4JRuntimeException("Cannot register reference type parameters if not a macro equivalent: "
                                             + "function annotation @XLFunction(isMacroEquivalent = true) required");
        }
        signature.append("U"); // XLOPER12 byref
      } else {
        signature.append("Q"); // XLOPER12 byval
      }
    }
    // Characters on the end -- we checked some invalid states at the start.
    if (isMacroEquivalent) {
      signature.append("#");
    } else if (isMTSafe) {
      signature.append("$");
    } else if (isVolatile) {
      signature.append("!");
    }
    return XLString.of(signature.toString());
  }

  /**
   * Build the function name string using the namespace if specified.
   * @param methodInvoker  the method invoker for this function, not null
   * @param namespaceAnnotation  the namespace annotation if there is one, or null if there isn't.
   * @param functionAnnotation  the function annoation is there is one, or null if there isn't.
   * @return the name of the function to register with Excel
   */
  private XLString buildFunctionName(final MethodInvoker methodInvoker, final XLNamespace namespaceAnnotation, final XLFunction functionAnnotation) {
    StringBuilder functionName = new StringBuilder();
    if (namespaceAnnotation != null) {
      functionName.append(namespaceAnnotation.value());
    }
    if (functionAnnotation != null) {
      if (functionAnnotation.name() != null) {
        functionName.append(functionAnnotation.name());
      } else {
        functionName.append(methodInvoker.getMethodName());
      }
    }
    return XLString.of(functionName.toString());
  }

  /**
   * Build the string containing a list of argument annotations.
   * @param argumentAnnotations  array of argument annotations, can contain nulls
   */
  private XLString buildArgNames(final XLArgument[] argumentAnnotations) {
    StringBuilder argumentNames = new StringBuilder();
    int argCounter = 1;
    
    for (int i = 0; i < argumentAnnotations.length; i++) {
      XLArgument argumentAnnotation = argumentAnnotations[i];
      if (argumentAnnotation != null) {
        if (argumentAnnotation.name() != null) {
          argumentNames.append(argumentAnnotation.name());
        } else {
          // TODO: try paranamer/JavaDocs?
          argumentNames.append(Integer.toString(argCounter));
        }
      } else {
        // TODO: try paranamer/JavaDocs?
        argumentNames.append(Integer.toString(argCounter));
      }
      if (i < argumentAnnotations.length - 1) {
        argumentNames.append(",");
      }
      argCounter++;
    }
    return XLString.of(argumentNames.toString());
  }
  
  /**
   * Get the type of the function
   * @param functionAnnotation the function annotation if there is one, null otherwise
   * @return the type, defaults to 1 (FUNCTION)
   */
  private XLInteger getFunctionType(final XLFunction functionAnnotation) {
    if (functionAnnotation != null) {
      return XLInteger.of(functionAnnotation.functionType().getExcelValue());
    } else {
      return XLInteger.of(XLFunctionType.FUNCTION.getExcelValue());
    }
  }

}