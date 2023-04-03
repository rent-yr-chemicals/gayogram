package com.cheogram.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.util.Log;

import com.google.android.vending.licensing.*;

import eu.siacs.conversations.utils.BiConsumer;

import eu.siacs.conversations.R;

public class CheogramLicenseChecker implements LicenseCheckerCallback {
	private final LicenseChecker mChecker;
	private final BiConsumer mCallback;

	public CheogramLicenseChecker(Context context, BiConsumer<String, String> callback) {
		mChecker = new LicenseChecker(context, new StrictPolicy(), context.getResources().getString(R.string.licensePublicKey));
		mCallback = callback;
	}

	public void checkLicense() {
		mChecker.checkAccess(this);
	}

	@Override
	public void dontAllow(int reason) {
		Log.d("CheogramLicenseChecker", "dontAllow: " + reason);
		mCallback.accept(null, null);
	}

	@Override
	public void applicationError(int errorCode) {
		Log.d("CheogramLicenseChecker", "applicationError: " + errorCode);
		mCallback.accept(null, null);
	}

	@Override
	public void allow(int reason, ResponseData data, String signedData, String signature) {
		Log.d("CheogramLicenseChecker", "" + reason + "	/ " + data + " / " + signedData + " / " + signature);
		mCallback.accept(signedData, signature);
	}
}
