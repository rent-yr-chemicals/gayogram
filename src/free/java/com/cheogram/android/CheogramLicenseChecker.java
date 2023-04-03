package com.cheogram.android;

import android.content.Context;
import android.util.Log;

import eu.siacs.conversations.utils.BiConsumer;

public class CheogramLicenseChecker {
	private BiConsumer<String, String> mCallback;

	public CheogramLicenseChecker(Context context, BiConsumer<String, String> callback) {
		mCallback = callback;
	}

	public void checkLicense() {
		Log.d("CheogramLicenseChecker", "skipping license checks in free build");
		mCallback.accept(null, null);
	}
}
