#include "stdafx.h"
#include "ExcelUtils.h"

void ExcelUtils::PrintXLOPER (XLOPER12 *pXLOper) {
	switch (pXLOper->xltype) {
	case xltypeStr: {
		size_t sz = pXLOper->val.str[0]; // the first 16-bit word is the length in chars (not inclusing any zero terminator)
		wchar_t *zeroTerminated = (wchar_t *)malloc ((sz + 1) * sizeof (wchar_t)); // + 1 for zero terminator
		wcsncpy_s (zeroTerminated, sz + 1, (const wchar_t *)pXLOper->val.str + 1, sz); // +1 to ptr to skip length 16 bit word.
		zeroTerminated[sz] = '\0'; // add a NULL terminator
		TRACE ("XLOPER12: xltypeStr: %s", zeroTerminated);
		free (zeroTerminated);
	} break;
	case xltypeNum: {
		TRACE ("XLOPER12: xltypeNum: %f", pXLOper->val.num);
	} break;
	case xltypeNil: {
		TRACE ("XLOPER12: xltypeNil");
	} break;
	case xltypeRef: {
		TRACE ("XLOPER12: xltypeRef: sheetId=%d", pXLOper->val.mref.idSheet);
		if (pXLOper->val.mref.lpmref == NULL) {
			TRACE ("  lpmref = NULL");
			break;
		}
		for (int i = 0; i < pXLOper->val.mref.lpmref->count; i++) {
			TRACE ("  rwFirst=%d,rwLast=%d,colFirst=%d,colLast=%d",
				pXLOper->val.mref.lpmref->reftbl[i].rwFirst,
				pXLOper->val.mref.lpmref->reftbl[i].rwLast,
				pXLOper->val.mref.lpmref->reftbl[i].colFirst,
				pXLOper->val.mref.lpmref->reftbl[i].colLast);
		}
	} break;
	case xltypeMissing: {
		TRACE ("XLOPER12: xltypeMissing");
	} break;
	case xltypeSRef: {
		TRACE ("XLOPER12: cltypeSRef: rwFirst=%d,rwLast=%d,colFirst=%d,colLast=%d",
			pXLOper->val.sref.ref.rwFirst,
			pXLOper->val.sref.ref.rwLast,
			pXLOper->val.sref.ref.colFirst,
			pXLOper->val.sref.ref.colLast);
	} break;
	case xltypeInt: {
		TRACE ("XLOPER12: xltypeInt: %d", pXLOper->val.w);
	} break;
	case xltypeErr: {
		TRACE ("XLOPER12: xltypeErr: %d", pXLOper->val.err);
	} break;
	case xltypeBool: {
		if (pXLOper->val.xbool == FALSE) {
			TRACE ("XLOPER12: xltypeBool: FALSE");
		} else {
			TRACE ("XLOPER12: xltypeBool: TRUE");
		}
	} break;
	case xltypeBigData: {
		TRACE ("XLOPER12: xltypeBigData");
	} break;
	case xltypeMulti: {
		RW cRows = pXLOper->val.array.rows;
		COL cCols = pXLOper->val.array.columns;
		TRACE ("XLOPER12: xltypeMulti: cols=%d, rows=%d", cCols, cRows);
		XLOPER12 *pXLOPER = pXLOper->val.array.lparray;
		for (RW j = 0; j < cRows; j++) {
			for (COL i = 0; i < cCols; i++) {
				PrintXLOPER (pXLOPER++);
			}
		}
	} break;
	default: {
		TRACE ("XLOPER12: Unrecognised XLOPER12 type %d", pXLOper->xltype);
	}

	}
}

void ExcelUtils::ScheduleCommand (wchar_t *wsCommandName, double dbSeconds) {
	XLOPER12 now;
	TRACE ("xlfNow");
	Excel12f (xlfNow, &now, 0);
	now.val.num += 2. / (3600. * 24.);
	XLOPER12 retVal;
	TRACE ("xlcOnTime");
	Excel12f (xlcOnTime, &retVal, 2, &now, TempStr12 (wsCommandName));
	TRACE ("xlcFree");
	Excel12f (xlFree, 0, 1, (LPXLOPER12)&now);
}

void ExcelUtils::RegisterCommand (XLOPER12 *xDLL, const wchar_t *wsCommandName) {
	FreeAllTempMemory ();
	XLOPER12 retVal;
	LPXLOPER12 exportName = TempStr12 (wsCommandName);
	//((LPXLOPER12)NULL)->val;
	LPXLOPER12 returnType = TempStr12 (TEXT ("J"));
	LPXLOPER12 commandName = TempStr12 (wsCommandName);
	LPXLOPER12 args = TempMissing12 ();
	LPXLOPER12 functionType = TempInt12 (2);
	TRACE ("xDLL = %p, exportName = %p, returnType = %p, commandName = %p, args = %p, functionType = %p", xDLL, exportName, returnType, commandName, args, functionType);

	Excel12f (
		xlfRegister, &retVal, 6, xDLL,
		exportName, // export name
		returnType, // return type, always J for commands
		commandName, // command name
		args, // args
		functionType // function type 2 = Command
		);
	TRACE ("After xlfRegister");
}


///***************************************************************************
// ExcelCursorProc()
//
// Purpose:
//
//      When a modal dialog box is displayed over Microsoft Excel's window, the
//      cursor is a busy cursor over Microsoft Excel's window. This WndProc traps
//      WM_SETCURSORs and changes the cursor back to a normal arrow.
//
// Parameters:
//
//      HWND hWndDlg        Contains the HWND Window
//      UINT message        The message to respond to
//      WPARAM wParam       Arguments passed by Windows
//      LPARAM lParam
//
// Returns: 
//
//      LRESULT             0 if message handled, otherwise the result of the
//                          default WndProc
//
// Comments:
//
// History:  Date       Author        Reason
///***************************************************************************

// Create a place to store Microsoft Excel's WndProc address //
WNDPROC ExcelUtils::g_lpfnExcelWndProc = NULL;

LRESULT CALLBACK ExcelUtils::ExcelCursorProc (HWND hwnd,
	UINT wMsg,
	WPARAM wParam,
	LPARAM lParam) {
	//
	// This block checks to see if the message that was passed in is a
	// WM_SETCURSOR message. If so, the cursor is set to an arrow; if not,
	// the default WndProc is called.
	//

	if (wMsg == WM_SETCURSOR) {
		SetCursor (LoadCursor (NULL, IDC_ARROW));
		return 0L;
	} else {
		return CallWindowProc (g_lpfnExcelWndProc, hwnd, wMsg, wParam, lParam);
	}
}

///***************************************************************************
// HookExcelWindow()
//
// Purpose:
//
//     This is the function that installs ExcelCursorProc so that it is
//     called before Microsoft Excel's main WndProc.
//
// Parameters:
//
//      HANDLE hWndExcel    This is a handle to Microsoft Excel's hWnd
//
// Returns: 
//
// Comments:
//
// History:  Date       Author        Reason
///***************************************************************************

void ExcelUtils::HookExcelWindow (HWND hWndExcel) {
	//
	// This block obtains the address of Microsoft Excel's WndProc through the
	// use of GetWindowLongPtr(). It stores this value in a global that can be
	// used to call the default WndProc and also to restore it. Finally, it
	// replaces this address with the address of ExcelCursorProc using
	// SetWindowLongPtr().
	//

	g_lpfnExcelWndProc = (WNDPROC)GetWindowLongPtr (hWndExcel, GWLP_WNDPROC);
	SetWindowLongPtr (hWndExcel, GWLP_WNDPROC, (LONG_PTR)(FARPROC)ExcelCursorProc);
}

///***************************************************************************
// UnhookExcelWindow()
//
// Purpose:
//
//      This is the function that removes the ExcelCursorProc that was
//      called before Microsoft Excel's main WndProc.
//
// Parameters:
//
//      HANDLE hWndExcel    This is a handle to Microsoft Excel's hWnd
//
// Returns: 
//
// Comments:
//
// History:  Date       Author        Reason
///***************************************************************************

void ExcelUtils::UnhookExcelWindow (HWND hWndExcel) {
	//
	// This function restores Microsoft Excel's default WndProc using
	// SetWindowLongPtr to restore the address that was saved into
	// g_lpfnExcelWndProc by HookExcelWindow(). It then sets g_lpfnExcelWndProc
	// to NULL.
	//

	SetWindowLongPtr (hWndExcel, GWLP_WNDPROC, (LONG_PTR)g_lpfnExcelWndProc);
	g_lpfnExcelWndProc = NULL;
}

HWND ExcelUtils::GetHWND () {
	XLOPER12 xWnd;
	Excel12f (xlGetHwnd, &xWnd, 0);
	return (HWND)xWnd.val.w;
}