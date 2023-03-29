package com.cheogram.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.util.Log;

import com.google.android.vending.licensing.*;

import eu.siacs.conversations.R;

public class CheogramLicenseChecker implements LicenseCheckerCallback {
	private final LicenseChecker mChecker;

	public CheogramLicenseChecker(Context context) {
		mChecker = new LicenseChecker(context, new StrictPolicy(), context.getResources().getString(R.string.licensePublicKey));
	}

	public void checkLicense() {
		mChecker.checkAccess(this);
	}

	@Override
	public void dontAllow(int reason) {
		Log.d("CheogramLicenseChecker", "dontAllow: " + reason);
	}

	@Override
	public void applicationError(int errorCode) {
		Log.d("CheogramLicenseChecker", "applicationError: " + errorCode);
	}

	@Override
	public void allow(int reason, ResponseData data, String signedData, String signature) {
		Log.d("CheogramLicenseChecker", "" + reason + "	/ " + data + " / " + signedData + " / " + signature);
	}
}
