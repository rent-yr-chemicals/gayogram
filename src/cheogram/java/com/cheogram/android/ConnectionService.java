package com.cheogram.android;

import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.DisconnectCause;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;

import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.ui.RtpSessionActivity;

public class ConnectionService extends android.telecom.ConnectionService {
	@Override
	public Connection onCreateOutgoingConnection(
		PhoneAccountHandle phoneAccountHandle,
		ConnectionRequest request
	) {
		String[] gateway = phoneAccountHandle.getId().split("/", 2);
		Connection connection = new CheogramConnection();
		connection.setAddress(
			request.getAddress(),
			TelecomManager.PRESENTATION_ALLOWED
		);
		connection.setAudioModeIsVoip(true);
		connection.setDialing();
		connection.setRingbackRequested(true);
		connection.setConnectionCapabilities(
			Connection.CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION
		);

		// TODO: jabber:iq:gateway
		String tel = request.getAddress().getSchemeSpecificPart().
		           replaceAll("[^\\+0-9]", "");
		if (!tel.startsWith("+1")) {
			tel = "+1" + tel;
		}

		// Instead of wiring the call up to the Android call UI,
		// just show our UI for now.  This means both are showing during a call.
		final Intent intent = new Intent(this, RtpSessionActivity.class);
		intent.setAction(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
		Bundle extras = new Bundle();
		extras.putString(
			RtpSessionActivity.EXTRA_ACCOUNT,
			Jid.of(gateway[0]).toEscapedString()
		);
		extras.putString(
			RtpSessionActivity.EXTRA_WITH,
			Jid.ofLocalAndDomain(tel, gateway[1]).toEscapedString()
		);
		extras.putBinder(
			RtpSessionActivity.EXTRA_CONNECTION_BINDER,
			new ConnectionBinder(connection)
		);
		intent.putExtras(extras);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);

		return connection;
	}

	public class ConnectionBinder extends android.os.Binder {
		protected Connection connection;

		public static final int TRANSACT_ACTIVE = android.os.IBinder.FIRST_CALL_TRANSACTION + 1;
		public static final int TRANSACT_DISCONNECT = TRANSACT_ACTIVE + 1;

		ConnectionBinder(Connection connection) {
			super();
			this.connection = connection;
		}

		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
			switch(code) {
				case TRANSACT_ACTIVE:
					this.connection.setActive();
					connection.setRingbackRequested(false);
					return true;
				case TRANSACT_DISCONNECT:
					this.connection.setDisconnected(
						new DisconnectCause(DisconnectCause.UNKNOWN)
					);
					return true;
				default:
					return false;
			}
		}
	}

	public class CheogramConnection extends Connection {
		@Override
		public void onDisconnect() {
			destroy();
		}

		@Override
		public void onAbort() {
			onDisconnect();
		}

		@Override
		public void onPlayDtmfTone(char c) {
			// TODO
		}
	}
}
