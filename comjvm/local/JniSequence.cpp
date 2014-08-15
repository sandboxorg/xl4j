/*
 * JVM as a COM object
 *
 * Copyright 2014 by Andrew Ian William Griffin <griffin@beerdragon.co.uk>
 * Released under the GNU General Public License.
 */

#include "stdafx.h"
#include "JniSequence.h"
#include "Internal.h"

void CJniValue::free () {
	switch (type) {
	case t_BSTR :
		SysFreeString (v._BSTR);
		break;
	}
}

void CJniValue::put_variant (const VARIANT *pvValue) {
	switch (pvValue->vt) {
	case VT_I4 :
		put_jint (pvValue->intVal);
		break;
	case VT_BOOL:
		put_jboolean (pvValue->boolVal);
		break;
	case VT_BSTR:
		put_BSTR(pvValue->bstrVal); // I'm assuming the caller thinks it's up to us to free it?
		break;
	case VT_I1:
		put_jbyte(pvValue->bVal);
		break;
	case VT_I2:
		put_jshort(pvValue->iVal);
		break;
	case VT_I8:
		put_jlong(pvValue->llVal);
		break;
	case VT_R4:
		put_jfloat(pvValue->fltVal);
		break;
	case VT_R8:
		put_jdouble(pvValue->dblVal);
		break;
	case VT_UI8:
		put_HANDLE(pvValue->ullVal);
		break;
	default :
		assert (0);
		break;
	}
}

void CJniValue::get_variant (VARIANT *pvValue) const {
	switch (type) {
	case t_jint :
		pvValue->vt = VT_I4;
		pvValue->intVal = v._jint;
		break;
	case t_jsize :
		pvValue->vt = VT_I4;
		pvValue->intVal = v._jsize;
		break;
	case t_BSTR:
		pvValue->vt = VT_BSTR;
		pvValue->bstrVal = v._BSTR;
		break;
	case t_jstring:  // TODO
		pvValue->vt = VT_BSTR;
		pvValue->bstrVal = SysAllocString ((OLECHAR *) v._jstring);
		assert(0);
		break;
	case t_jboolean:
		pvValue->vt = VT_BOOL;
		pvValue->boolVal = v._jboolean;
		break;
	case t_jbyte:
		pvValue->vt = VT_I1;
		pvValue->bVal = v._jbyte;
		break;
	case t_jchar:
		pvValue->vt = VT_UI2; // jchar as unsigned short, effectively.
		pvValue->uiVal = v._jchar;
		break;
	case t_jshort:
		pvValue->vt = VT_I2; // signed short.
		pvValue->iVal = v._jshort;
		break;
	case t_jlong:
		pvValue->vt = VT_I8;
		pvValue->llVal = v._jlong;
		break;
	case t_jfloat:
		pvValue->vt = VT_R4;
		pvValue->fltVal = v._jfloat;
		break;
	case t_jdouble:
		pvValue->vt = VT_R8;
		pvValue->dblVal = v._jdouble;
		break;
	case t_jclass:
	case t_jobject:
	case t_jmethodID:
	case t_jfieldID:
	case t_jobjectRefType:
	case t_jthrowable:
	case t_jobjectArray:
	case t_jbooleanArray:
	case t_jbyteArray:
	case t_jcharArray:
	case t_jshortArray:
	case t_jintArray:
	case t_jlongArray:
	case t_jfloatArray:
	case t_jdoubleArray:
	case t_jweak:
		pvValue->vt = VT_UI8;
		pvValue->ullVal = (ULONGLONG)v._jclass; // ick
		break;
	default :
		assert (0);
		break;
	}
}

void CJniValue::copy_into (CJniValue &value) const {
	switch (type) {
	case t_BSTR :
		value.put_BSTR (v._BSTR);
		break;
	default :
		value.reset (type);
		value.v = v;
		break;
	}
}

CJniValue::CJniValue (BSTR bstr)
: type (t_BSTR) {
	v._BSTR = SysAllocStringLen (bstr, SysStringLen (bstr));
	if (!v._BSTR) throw std::bad_alloc ();
}

void CJniValue::put_BSTR (BSTR bstr) {
	BSTR bstrCopy = SysAllocStringLen (bstr, SysStringLen (bstr));
	if (bstrCopy) {
		reset (t_BSTR);
		v._BSTR = bstrCopy;
	} else {
		reset (t_nothing);
	}
}

void CJniValue::put_HANDLE (ULONGLONG handle) {
	if (handle) {
		reset (t_HANDLE);
		v._HANDLE = handle;
    } else {
		reset (t_nothing);
	}
}

const jchar *CJniValue::get_pjchar () const {
	switch (type) {
	case t_BSTR :
		return (unsigned short*)v._BSTR;
	}
	assert (0);
	return 0;
}

/// Caller must free char * returned when finished with SysFreeString
const char *CJniValue::get_alloc_pchar() const { 
	switch (type) {
	case t_BSTR:
		return _com_util::ConvertBSTRToString(v._BSTR); 
	}
	assert(0);
	return 0;
}



HRESULT CJniValue::load (std::vector<CJniValue> &aValue) {
	try {
		aValue.push_back (*this);
		type = t_nothing;
		return S_OK;
	} catch (std::bad_alloc) {
		return E_OUTOFMEMORY;
	}
}

CJniSequence::CJniSequence (CJvm *pJvm)
	: m_lRefCount (1), m_pJvm (pJvm), m_cValue (0), m_cExecuting (0), m_cArgument (0), m_cResult (0) {
	IncrementActiveObjectCount ();
	pJvm->AddRef ();
	InitializeCriticalSection (&m_cs);
}

CJniSequence::~CJniSequence () {
	assert (m_lRefCount == 0);
	size_t c = m_ahSemaphore.size (), i;
	for (i= 0; i< c; i++) {
		CloseHandle (m_ahSemaphore[i]);
	}
	DeleteCriticalSection (&m_cs);
	m_pJvm->Release ();
	DecrementActiveObjectCount ();
}

/// <summary>Add an operation to the run queue.</summary>
///
/// <para>The caller must hold the critical section.</para>
///
/// <param name="operation">Operation to add</param>
/// <returns>S_OK if successful, an error code otherwise.</returns>
HRESULT CJniSequence::AddOperation (JniOperation operation) {
	try {
		m_aOperation.push_back (operation);
		return S_OK;
	} catch (std::bad_alloc) {
		return E_OUTOFMEMORY;
	}
}

/// <summary>Add an operation to the run queue with a single parameter.</summary>
///
/// <para>The caller must hold the critical section.</para>
///
/// <param name="operation">Operation to add</param>
/// <param name="lParam">Parameter index</param>
/// <returns>S_OK if successful, an error code otherwise.</returns>
HRESULT CJniSequence::AddOperation (JniOperation operation, long lParam) {
	bool bOperation = false;
	try {
		m_aOperation.push_back (operation);
		bOperation = true;
		m_aParam.push_back (lParam);
		return S_OK;
	} catch (std::bad_alloc) {
		if (bOperation) m_aOperation.pop_back ();
		return E_OUTOFMEMORY;
	}
}

/// <summary>Add an operation to the run queue with a two parameters.</summary>
///
/// <para>The caller must hold the critical section.</para>
///
/// <param name="operation">Operation to add</param>
/// <param name="lParam1">First parameter index</param>
/// <param name="lParam2">Second parameter index</param>
/// <returns>S_OK if successful, an error code otherwise.</returns>
HRESULT CJniSequence::AddOperation (JniOperation operation, long lParam1, long lParam2) {
	bool bOperation = false;
	bool bParam = false;
	try {
		m_aOperation.push_back (operation);
		bOperation = true;
		m_aParam.push_back (lParam1);
		bParam = true;
		m_aParam.push_back (lParam2);
		return S_OK;
	} catch (std::bad_alloc) {
		if (bOperation) m_aOperation.pop_back ();
		if (bParam) m_aParam.pop_back ();
		return E_OUTOFMEMORY;
	}
}

/// <summary>Loads a constant into the value buffer.</summary>
///
/// <para>The caller must hold the critical section.</para>
///
/// <param name="value">Constant to load</param>
/// <param name="plRef">Receives the loaded value index</param>
/// <returns>S_OK if successful, an error code otherwise</returns>
HRESULT CJniSequence::LoadConstant (CJniValue &value, long *plRef) {
	HRESULT hr = value.load (m_aConstant);
	if (SUCCEEDED (hr)) {
		hr = AddOperation (JniOperation::io_LoadConstant);
		if (SUCCEEDED (hr)) {
			*plRef = m_cValue++;
		}
	}
	return hr;
}

/// <summary>Marks the start of an execution</summary>
///
/// <returns>Notification semaphore</returns>
HANDLE CJniSequence::BeginExecution () {
	EnterCriticalSection (&m_cs);
	m_cExecuting++;
	HANDLE hSemaphore;
	if (m_ahSemaphore.size () > 0) {
		hSemaphore = m_ahSemaphore[m_ahSemaphore.size () - 1];
		m_ahSemaphore.pop_back ();
	} else {
		hSemaphore = CreateSemaphore (NULL, 0, 1, NULL);
	}
	LeaveCriticalSection (&m_cs);
	return hSemaphore;
}

/// <summary>Marks the end of an execution</summary>
///
/// <param name="hSemaphore">Notification semaphore returned by BeginExecution</param>
void CJniSequence::EndExecution (HANDLE hSemaphore) {
	EnterCriticalSection (&m_cs);
	m_cExecuting--;
	try {
		m_ahSemaphore.push_back (hSemaphore);
	} catch (std::bad_alloc) {
		CloseHandle (hSemaphore);
	}
	LeaveCriticalSection (&m_cs);
}

HRESULT STDMETHODCALLTYPE CJniSequence::QueryInterface (
	/* [in] */ REFIID riid,
	/* [iid_is][out] */ _COM_Outptr_ void __RPC_FAR *__RPC_FAR *ppvObject
	) {
	if (!ppvObject) return E_POINTER;
	if (riid == IID_IUnknown) {
		*ppvObject = static_cast<IUnknown*> (this);
	} else if (riid == IID_IJniSequence) {
		*ppvObject = static_cast<IJniSequence*> (this);
	} else {
		*ppvObject = NULL;
		return E_NOINTERFACE;
	}
	AddRef ();
	return S_OK;
}

ULONG STDMETHODCALLTYPE CJniSequence::AddRef () {
	return InterlockedIncrement (&m_lRefCount);
}

ULONG STDMETHODCALLTYPE CJniSequence::Release () {
	ULONG lResult = InterlockedDecrement (&m_lRefCount);
	if (!lResult) delete this;
	return lResult;
}

/* [propget] */ HRESULT STDMETHODCALLTYPE CJniSequence::get_Arguments ( 
    /* [retval][out] */ long *pcArgs
	) {
	if (!pcArgs) return E_POINTER;
	EnterCriticalSection (&m_cs);
	*pcArgs = (long)m_cArgument;
	LeaveCriticalSection (&m_cs);
	return S_OK;
}

/* [propget] */ HRESULT STDMETHODCALLTYPE CJniSequence::get_Results ( 
    /* [retval][out] */ long *pcResults
	) {
	if (!pcResults) return E_POINTER;
	EnterCriticalSection (&m_cs);
	*pcResults = (long)m_cResult;
	LeaveCriticalSection (&m_cs);
	return S_OK;
}

CJniSequenceExecutor::CJniSequenceExecutor (CJniSequence *pOwner, long cArgs, VARIANT *pArgs, long cResults, VARIANT *pResults)
: m_lRefCount (1), m_pOwner (pOwner), m_cArgs (cArgs), m_pArgs (pArgs), m_cResults (cResults), m_pResults (pResults) {
	m_hSemaphore = pOwner->BeginExecution ();
	pOwner->AddRef ();
}

CJniSequenceExecutor::~CJniSequenceExecutor () {
	m_pOwner->EndExecution (m_hSemaphore);
	m_pOwner->Release ();
}

HRESULT CJniSequenceExecutor::Run (JNIEnv *pEnv) {
	HRESULT hr;
	try {
		long cValue = 0;
		std::vector<long>::const_iterator params = m_pOwner->Params ()->begin ();
		std::vector<CJniValue>::const_iterator constants = m_pOwner->Constants ()->begin ();
		std::vector<CJniValue> aValues (m_pOwner->Values ());
		for (std::vector<JniOperation>::const_iterator itr = m_pOwner->Operations ()->begin (), end = m_pOwner->Operations ()->end (); itr != end; itr++) {
			switch (*itr) {
			case JniOperation::io_LoadArgument 
				: {
					long lValueRef = *(params++);
					if (m_cArgs > 0) {
						m_cArgs--;
						aValues[lValueRef].put_variant(m_pArgs++);
					}
					break;
				}
			case JniOperation::io_LoadConstant :
				(constants++)->copy_into (aValues[cValue++]);
				break;
			case JniOperation::io_StoreResult
				: {
					long lValueRef = *(params++);
					if (m_cResults > 0) {
						m_cResults--;
						aValues[lValueRef].get_variant (m_pResults++);
					}
					break;
				}
			case JniOperation::jni_GetVersion :
				aValues[cValue++].put_jint (pEnv->GetVersion ());
				break;
			case JniOperation::jni_NewString
				: {
					const jchar *unicode = aValues[*(params++)].get_pjchar ();
					jsize len = aValues[*(params++)].get_jsize ();
					aValues[cValue++].put_jstring (pEnv->NewString (unicode, len));
					break;
				}
			case JniOperation::jni_GetStringLength
				: {
					jstring str = aValues[*(params++)].get_jstring ();
					aValues[cValue++].put_jsize (pEnv->GetStringLength (str));
					break;
				}
			case JniOperation::jni_FindClass
				: {
					const char *name = aValues[*(params++)].get_alloc_pchar ();
					jclass clazz = pEnv->FindClass(name);
					

					aValues[cValue++].put_jclass(clazz);
					break;
				}
			default :
				assert (0);
				break;
			}
		}
		hr = S_OK;
	} catch (std::bad_alloc) {
		hr = E_OUTOFMEMORY;
	}
	ReleaseSemaphore (m_hSemaphore, 1, NULL);
	return hr;
}

void CJniSequenceExecutor::Wait () {
	DWORD dwStatus = WaitForSingleObject (m_hSemaphore, INFINITE);
	assert (dwStatus == WAIT_OBJECT_0);
}

void CJniSequenceExecutor::AddRef () {
	InterlockedIncrement (&m_lRefCount);
}

void CJniSequenceExecutor::Release () {
	long lCount = InterlockedDecrement (&m_lRefCount);
	if (!lCount) delete this;
}

static HRESULT APIENTRY _Execute (LPVOID lpData, JNIEnv *pEnv) {
	CJniSequenceExecutor *pExecutor = (CJniSequenceExecutor*)lpData;
	HRESULT hr = pExecutor->Run (pEnv);
	pExecutor->Release ();
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::Execute ( 
    /* [in] */ long cArgs,
    /* [size_is][in] */ VARIANT *aArgs,
    /* [in] */ long cResults,
    /* [size_is][out] */ VARIANT *aResults
	) {
	if (cArgs && !aArgs) return E_POINTER;
	if (cResults && !aResults) return E_POINTER;
	HRESULT hr;
	try {
		long l;
		for (l = 0; l < cResults; l++) {
			VariantClear (aResults + l);
		}
		CJniSequenceExecutor *pExecutor = new CJniSequenceExecutor (this, cArgs, aArgs, cResults, aResults);
		pExecutor->AddRef ();
		hr = m_pJvm->Execute (_Execute, pExecutor);
		if (SUCCEEDED (hr)) {
			pExecutor->Wait ();
			hr = S_OK;
		} else {
			pExecutor->Release ();
		}
		pExecutor->Release ();
	} catch (std::bad_alloc) {
		hr = E_OUTOFMEMORY;
	}
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::Argument ( 
    /* [retval][out] */ long *plValueRef
	) {
	HRESULT hr;
	EnterCriticalSection(&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else {
		hr = AddOperation(JniOperation::io_LoadArgument);
		if (SUCCEEDED(hr)) {
			*plValueRef = m_cArgument++;
		}
		//hr = S_OK;
	}
	LeaveCriticalSection(&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::Result ( 
    /* [in] */ long lValueRef
	) {
	HRESULT hr;
	EnterCriticalSection (&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else if ((lValueRef < 0) || (lValueRef >= (long)m_cValue)) {
		hr = E_INVALIDARG;
	} else {
		hr = AddOperation (JniOperation::io_StoreResult, lValueRef);
		if (SUCCEEDED (hr)) {
			m_cResult++;  // isn't this already done inside the operation?
		}
	}
	LeaveCriticalSection (&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::StringConstant (
    /* [in] */ BSTR bstr,
    /* [retval][out] */ long *plValueRef
	) {
	if (!bstr) return E_POINTER;
	if (!plValueRef) return E_POINTER;
	HRESULT hr;
	EnterCriticalSection (&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else {
		BSTR bstrCopy = SysAllocStringLen (bstr, SysStringLen (bstr));
		if (bstrCopy) {
			CJniValue constant (bstrCopy);
			hr = LoadConstant (constant, plValueRef);
		} else {
			hr = E_OUTOFMEMORY;
		}
	}
	LeaveCriticalSection (&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::ByteConstant (
    /* [in] */ byte nValue,
    /* [retval][out] */ long *plValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::ShortConstant ( 
    /* [in] */ short nValue,
    /* [retval][out] */ long *plValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::IntConstant ( 
    /* [in] */ long nValue,
    /* [retval][out] */ long *plValueRef
	) {
	if (!plValueRef) return E_POINTER;
	HRESULT hr;
	EnterCriticalSection (&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else {
		CJniValue constant (nValue);
		hr = LoadConstant (constant, plValueRef);
	}
	LeaveCriticalSection (&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::LongConstant ( 
    /* [in] */ hyper nValue,
    /* [retval][out] */ long *plValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::BooleanConstant ( 
    /* [in] */ BOOL fValue,
    /* [retval][out] */ long *plValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetVersion ( 
    /* [out] */ long *plValueRef
	) {
	if (!plValueRef) return E_POINTER;
	HRESULT hr;
	EnterCriticalSection (&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else {
		hr = AddOperation (JniOperation::jni_GetVersion);
		if (SUCCEEDED (hr)) {
			*plValueRef = m_cValue++;
		}
	}
	LeaveCriticalSection (&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_DefineClass ( 
    /* [in] */ long lNameRef,
    /* [in] */ long lLoaderRef,
    /* [in] */ long lBufRef,
    /* [in] */ long lLenRef,
    /* [retval][out] */ long *plClassRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_FindClass ( 
    /* [in] */ long lNameRef,
    /* [retval][out] */ long *plClassRef
	) {
	if (!plClassRef) return E_POINTER;
	HRESULT hr;
	EnterCriticalSection (&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else {
		hr = AddOperation (JniOperation::jni_FindClass);
		if (SUCCEEDED (hr)) {
			*plClassRef = m_cValue++;
		}
	}
	LeaveCriticalSection (&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_FromReflectedMethod ( 
    /* [in] */ long lMethodRef,
    /* [retval][out] */ long *plMethodIDRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_FromReflectedField ( 
    /* [in] */ long lFieldRef,
    /* [retval][out] */ long *plFieldIDRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ToReflectedMethod ( 
    /* [in] */ long lClsRef,
    /* [in] */ long lMethodIDRef,
    /* [in] */ long lIsStaticRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetSuperclass ( 
    /* [in] */ long lSubRef,
    /* [retval][out] */ long *plClassRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_IsAssignableFrom ( 
    /* [in] */ long lSubRef,
    /* [in] */ long lSupRef,
    /* [retval][out] */ long *plBooleanRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ToReflectedField ( 
    /* [in] */ long lClsRef,
    /* [in] */ long lFieldIDRef,
    /* [in] */ long lIsStaticRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_Throw ( 
    /* [in] */ long lObjRef,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ThrowNew ( 
    /* [in] */ long lClassRef,
    /* [in] */ long lMsgRef,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ExceptionOccurred ( 
    /* [retval][out] */ long *plThrowableRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ExceptionDescribe () {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ExceptionClear () {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_FatalError ( 
    /* [in] */ long lMsgRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_PushLocalFrame ( 
    /* [in] */ long lCapacityRef,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_PopLocalFrame ( 
    /* [in] */ long lResultRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewGlobalRef ( 
    /* [in] */ long lLobjRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_DeleteGlobalRef ( 
    /* [in] */ long lGrefRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_DeleteLocalRef ( 
    /* [in] */ long lObjRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_IsSameObject ( 
    /* [in] */ long lObj1Ref,
    /* [in] */ long lObj2Ref,
    /* [retval][out] */ long *plBooleanRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewLocalRef ( 
    /* [in] */ long lRefRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_EnsureLocalCapacity ( 
    /* [in] */ long lCapacityRet,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_AllocObject ( 
    /* [in] */ long lClassRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewObject ( 
    /* [in] */ long lClassRef,
    /* [in] */ long lMethodIDRef,
    /* [in] */ long cArgs,
    /* [size_is][in] */ long *alArgRefs,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetObjectClass ( 
    /* [in] */ long lObjRef,
    /* [retval][out] */ long *plClassRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_IsInstanceOf ( 
    /* [in] */ long lObjRef,
    /* [in] */ long lClassRef,
    /* [retval][out] */ long *plBooleanRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetMethodID ( 
    /* [in] */ long lClassRef,
    /* [in] */ long lNameRef,
    /* [in] */ long lSigRef,
    /* [retval][out] */ long *plMethodIDRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_CallMethod ( 
	/* [in] */ long lType,
    /* [in] */ long lObjRef,
    /* [in] */ long lMethodIDRef,
    /* [in] */ long cArgs,
    /* [size_is][in] */ long *alArgRefs,
    /* [retval][out] */ long *plResultRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_CallNonVirtualMethod ( 
	/* [in] */ long lType,
    /* [in] */ long lObjRef,
    /* [in] */ long lClassRef,
    /* [in] */ long lMethodIDRef,
    /* [in] */ long cArgs,
    /* [size_is][in] */ long *alArgRefs,
    /* [retval][out] */ long *plResultRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetFieldID ( 
    /* [in] */ long lClassRef,
    /* [in] */ long lNameRef,
    /* [in] */ long lSigRef,
    /* [retval][out] */ long *plFieldIDRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetField ( 
	/* [in] */ long lType,
    /* [in] */ long lObjRef,
    /* [in] */ long lFieldIDRef,
    /* [retval][out] */ long *plResultRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_SetField ( 
	/* [in] */ long lType,
    /* [in] */ long lObjRef,
    /* [in] */ long lFieldIDRef,
    /* [in] */ long lValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStaticMethodID ( 
    /* [in] */ long lClassRef,
    /* [in] */ long lNameRef,
    /* [in] */ long lSigRef,
    /* [retval][out] */ long *plMethodIDRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_CallStaticMethod ( 
	/* [in] */ long lType,
    /* [in] */ long lClassRef,
    /* [in] */ long lMethodIDRef,
    /* [in] */ long cArgs,
    /* [size_is][in] */ long *alArgsRef,
    /* [retval][out] */ long *plResultRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStaticFieldID ( 
    /* [in] */ long lClassRef,
    /* [in] */ long lNameRef,
    /* [in] */ long lSigRef,
    /* [retval][out] */ long *plFieldIDRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStaticField ( 
	/* [in] */ long lType,
    /* [in] */ long lClassRef,
    /* [in] */ long lFieldIDRef,
    /* [retval][out] */ long *plValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_SetStaticField ( 
	/* [in] */ long lType,
    /* [in] */ long lClassRef,
    /* [in] */ long lFieldIDRef,
    /* [in] */ long lValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewString ( 
    /* [in] */ long lUnicodeRef,
    /* [in] */ long lSizeRef,
    /* [retval][out] */ long *plStringRef
	) {
	if (!plStringRef) return E_POINTER;
	HRESULT hr;
	EnterCriticalSection (&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else {
		hr = AddOperation (JniOperation::jni_NewString, lUnicodeRef, lSizeRef);
		if (SUCCEEDED (hr)) {
			*plStringRef = m_cValue++;
		}
	}
	LeaveCriticalSection (&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStringLength ( 
    /* [in] */ long lStrRef,
    /* [retval][out] */ long *plSizeRef
	) {
	if (!plSizeRef) return E_POINTER;
	HRESULT hr;
	EnterCriticalSection (&m_cs);
	if (m_cExecuting) {
		hr = E_NOT_VALID_STATE;
	} else {
		hr = AddOperation (JniOperation::jni_GetStringLength, lStrRef);
		if (SUCCEEDED (hr)) {
			*plSizeRef = m_cValue++;
		}
	}
	LeaveCriticalSection (&m_cs);
	return hr;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStringChars ( 
    /* [in] */ long lStrRef,
    /* [out] */ long *plIsCopyRef,
    /* [retval][out] */ long *plCharRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ReleaseStringChars ( 
    /* [in] */ long lStrRef,
    /* [in] */ long lCharsRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewStringUTF ( 
    /* [in] */ long lUtfRef,
    /* [retval][out] */ long *plStringRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStringUTFLength ( 
    /* [in] */ long lStrRef,
    /* [retval][out] */ long *plSizeRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStringUTFChars ( 
    /* [in] */ long lStrRef,
    /* [out] */ long *plIsCopyRef,
    /* [retval][out] */ long *plCharRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ReleaseStringUTFChars ( 
    /* [in] */ long lStrRef,
    /* [in] */ long lCharsRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetArrayLength ( 
    /* [in] */ long lArrayRef,
    /* [retval][out] */ long *plSizeRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewObjectArray ( 
    /* [in] */ long lLenRef,
    /* [in] */ long lClassRef,
    /* [in] */ long lInitRef,
    /* [retval][out] */ long *plObjectArrayRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetObjectArrayElement ( 
    /* [in] */ long lArrayRef,
    /* [in] */ long lIndexRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_SetObjectArrayElement ( 
    /* [in] */ long lArrayRef,
    /* [in] */ long lIndexRef,
    /* [in] */ long lValRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewArray ( 
	/* [in] */ long lType,
    /* [in] */ long lLenRef,
    /* [retval][out] */ long *plArrayRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetArrayElements ( 
	/* [in] */ long lType,
    /* [in] */ long lArrayRef,
    /* [out] */ long *plIsCopyRef,
    /* [retval][out] */ long *plValueRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ReleaseArrayElements ( 
	/* [in] */ long lType,
    /* [in] */ long lArrayRef,
    /* [in] */ long lElemsRef,
    /* [in] */ long lModeRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetArrayRegion ( 
	/* [in] */ long lType,
    /* [in] */ long lArrayRef,
    /* [in] */ long lStartRef,
    /* [in] */ long lLRef,
    /* [in] */ long lBufRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_SetArrayRegion ( 
	/* [in] */ long lType,
    /* [in] */ long lArrayRef,
    /* [in] */ long lStartRef,
    /* [in] */ long lLRef,
    /* [in] */ long lBufRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_RegisterNatives ( 
    /* [in] */ long lClassRef,
    /* [in] */ long lMethodsRef,
    /* [in] */ long lNMethodsef,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_UnregisterNatives ( 
    /* [in] */ long lClassRef,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_MonitorEntry ( 
    /* [in] */ long lObjRef,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_MonitorExit ( 
    /* [in] */ long lObjRef,
    /* [retval][out] */ long *plIntRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStringRegion ( 
    /* [in] */ long lStrRef,
    /* [in] */ long lStartRef,
    /* [in] */ long lLenRef,
    /* [in] */ long lBufRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStringUTFRegion ( 
    /* [in] */ long lStrRef,
    /* [in] */ long lStartRef,
    /* [in] */ long lLenRef,
    /* [in] */ long lBufRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetPrimitiveArrayCritical ( 
    /* [in] */ long lArrayRef,
    /* [out] */ long *plIsCopyRef,
    /* [retval][out] */ long *plVoidRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ReleasePrimitiveArrayCritical ( 
    /* [in] */ long lArrayRef,
    /* [in] */ long lCArrayRef,
    /* [in] */ long lModeRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetStringCritical ( 
    /* [in] */ long lStringRef,
    /* [out] */ long *plIsCopyRef,
    /* [retval][out] */ long *plCharRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ReleaseStringCritical ( 
    /* [in] */ long lStringRef,
    /* [in] */ long lCStringRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewWeakGlobalRef ( 
    /* [in] */ long lObjRef,
    /* [retval][out] */ long *plWeakRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_DeleteWeakGlobalRef ( 
    /* [in] */ long lRefRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_ExceptionCheck ( 
    /* [retval][out] */ long *plBooleanRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_NewDirectByteBuffer ( 
    /* [in] */ long lAddressRef,
    /* [in] */ long lCapacityRef,
    /* [retval][out] */ long *plObjectRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetDirectBufferAddress ( 
    /* [in] */ long lBufRef,
    /* [retval][out] */ long *plVoidRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetDirectBufferCapacity ( 
    /* [in] */ long lBufRef,
    /* [retval][out] */ long *plLongRef
	) {
	// TODO
	return E_NOTIMPL;
}

HRESULT STDMETHODCALLTYPE CJniSequence::jni_GetObjectRefType ( 
    /* [in] */ long lObjRef,
    /* [retval][out] */ long *plObjectRefTypeRef
	) {
	// TODO
	return E_NOTIMPL;
}
