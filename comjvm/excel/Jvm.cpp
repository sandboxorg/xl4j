#include "stdafx.h"
#define COMJVM_EXCEL_EXPORT
#include "Jvm.h"
#include "helper/ClasspathUtils.h"
#include "../utils/FileUtils.h"
#include <core/Settings.h>

Jvm::Jvm () : m_lRefCount (1) {

	HRESULT hr = ComJvmCreateLocalConnector (&m_pConnector);
	if (FAILED (hr)) {
		LOGERROR ("CreateLocalConnector failed");
		_com_raise_error (hr);
	} else {
		LOGTRACE ("CreateLocalConnector succeeded");
	}
	hr = m_pConnector->Lock ();
	if (FAILED (hr)) {
		LOGERROR ("connector Lock could not be aquired");
		_com_raise_error (hr);
	} else {
		LOGTRACE ("Connector lock aquired");
	}

	IJvmTemplate *pTemplate;

	hr = ComJvmCreateTemplate (TEXT("default"), &pTemplate);
	if (FAILED (hr)) {
		LOGERROR ("could not create template");
		_com_raise_error (hr);
	} else {
		pTemplate->AddRef ();
		LOGTRACE ("Created template");
	}
	IClasspathEntries *entries;
	hr = pTemplate->get_Classpath (&entries);
	if (FAILED (hr)) {
		LOGERROR ("could not get template classpath");
		_com_raise_error (hr);
	} else {
		LOGTRACE ("Got classpath entries");
		entries->AddRef ();
	}
	wchar_t szLibPath[MAX_PATH];
	if (FAILED (hr = FileUtils::GetAddinAbsolutePath (szLibPath, MAX_PATH, TEXT ("..\\lib\\")))) {
		LOGERROR ("Could not create add-in relative path");
		_com_raise_error (hr);
	}
	LOGTRACE ("Calling ClasspathUtils::AddEntries with %s", szLibPath);
	ClasspathUtils::AddEntries (entries, szLibPath);
	wchar_t szXL4JPath[MAX_PATH];
	if (FAILED (hr = FileUtils::GetAddinAbsolutePath (szXL4JPath, MAX_PATH, TEXT ("..\\..\\..\\target\\excel4j-0.1.0-SNAPSHOT.jar")))) {
		LOGERROR ("Could not crate add-in relative path for xl4j jar");
		_com_raise_error (hr);
	}
	LOGTRACE ("Calling Classpathutils::AddEntries with %s", szXL4JPath);
	ClasspathUtils::AddEntry (entries, szXL4JPath);
	// Load settings.
	hr = m_pConnector->CreateJvm (pTemplate, NULL, &m_pJvm);
	if (FAILED (hr)) {
		_com_error err (hr);
		LPCTSTR errMsg = err.ErrorMessage ();
		LOGTRACE ("could not create JVM: %s", errMsg);
		_com_raise_error (hr);
	}
	//m_pJvm->AddRef ();
	LOGTRACE ("Created JVM!");
	m_pConnector->Unlock ();
	LOGTRACE ("Unlocked connector");

	hr = pTemplate->Release ();
	if (FAILED (hr)) {
		LOGERROR ("Could not release template");
		_com_raise_error (hr);
	} else {
		LOGTRACE ("Released template");
	}
	hr = entries->Release ();
	if (FAILED (hr)) {
		LOGERROR ("Could not release classpath entries");
		_com_raise_error (hr);
	} else {
		LOGTRACE ("Released classpath entries");
	}
}

Jvm::~Jvm () {
	LOGTRACE ("Releasing JVM");
	m_pJvm->Release ();
	LOGTRACE ("Releasing Connector");
	m_pConnector->Release ();
}

ULONG Jvm::AddRef () {
	return InterlockedIncrement (&m_lRefCount);
}

ULONG Jvm::Release () {
	ULONG lResult = InterlockedDecrement (&m_lRefCount);
	if (!lResult) {
		LOGTRACE ("Refcount 0, deleting this");
		delete this;
	} else {
		LOGTRACE ("Refcount not 0, it's %d", m_lRefCount);
	}
	return lResult;
}