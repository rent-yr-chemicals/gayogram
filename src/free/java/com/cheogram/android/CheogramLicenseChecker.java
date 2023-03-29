package com.cheogram.android;

import android.content.Context;
import android.util.Log;

public class CheogramLicenseChecker {
	public CheogramLicenseChecker(Context context) { }

	public void checkLicense() {
		Log.d("CheogramLicenseChecker", "skipping license checks in free build");
	}
}
