package com.cheogram.android;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import com.google.common.collect.ImmutableSet;

import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;

import android.Manifest;
import androidx.core.content.ContextCompat;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import com.intentfilter.androidpermissions.PermissionManager;
import com.intentfilter.androidpermissions.NotificationSettings;
import com.intentfilter.androidpermissions.models.DeniedPermissions;
import io.michaelrocks.libphonenumber.android.NumberParseException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.AppRTCAudioManager;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;

public class ConnectionService extends android.telecom.ConnectionService {
	public XmppConnectionService xmppConnectionService = null;
	protected ServiceConnection mConnection = new ServiceConnection() {
		@Override
			public void onServiceConnected(ComponentName className, IBinder service) {
				XmppConnectionBinder binder = (XmppConnectionBinder) service;
				xmppConnectionService = binder.getService();
			}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			xmppConnectionService = null;
		}
	};

	@Override
	public void onCreate() {
		// From XmppActivity.connectToBackend
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction("ui");
		try {
			startService(intent);
		} catch (IllegalStateException e) {
			Log.w("com.cheogram.android.ConnectionService", "unable to start service from " + getClass().getSimpleName());
		}
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		unbindService(mConnection);
	}

	@Override
	public Connection onCreateOutgoingConnection(
		PhoneAccountHandle phoneAccountHandle,
		ConnectionRequest request
	) {
		String[] gateway = phoneAccountHandle.getId().split("/", 2);

		String rawTel = request.getAddress().getSchemeSpecificPart();
		String postDial = PhoneNumberUtils.extractPostDialPortion(rawTel);

		String tel = PhoneNumberUtils.extractNetworkPortion(rawTel);
		try {
			tel = PhoneNumberUtilWrapper.normalize(this, tel);
		} catch (NumberParseException e) {
			return Connection.createFailedConnection(
				new DisconnectCause(DisconnectCause.ERROR)
			);
		}

		if (xmppConnectionService.getJingleConnectionManager().isBusy() != null) {
			return Connection.createFailedConnection(
				new DisconnectCause(DisconnectCause.BUSY)
			);
		}

		Account account = xmppConnectionService.findAccountByJid(Jid.of(gateway[0]));
		Jid with = Jid.ofLocalAndDomain(tel, gateway[1]);
		CheogramConnection connection = new CheogramConnection(account, with, postDial);

		PermissionManager permissionManager = PermissionManager.getInstance(this);
		permissionManager.setNotificationSettings(
			new NotificationSettings.Builder()
				.withMessage(R.string.microphone_permission_for_call)
				.withSmallIcon(R.drawable.ic_notification).build()
		);

		Set<String> permissions = new HashSet<>();
		permissions.add(Manifest.permission.RECORD_AUDIO);
		permissionManager.checkPermissions(permissions, new PermissionManager.PermissionRequestListener() {
			@Override
			public void onPermissionGranted() {
				connection.setSessionId(xmppConnectionService.getJingleConnectionManager().proposeJingleRtpSession(
					account,
					with,
					ImmutableSet.of(Media.AUDIO)
				));
			}

			@Override
			public void onPermissionDenied(DeniedPermissions deniedPermissions) {
				connection.setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
			}
		});

		connection.setInitializing();
		connection.setAddress(
			Uri.fromParts("tel", tel, null), // Normalized tel as tel: URI
			TelecomManager.PRESENTATION_ALLOWED
		);

		xmppConnectionService.setOnRtpConnectionUpdateListener(
			(XmppConnectionService.OnJingleRtpConnectionUpdate) connection
		);

		return connection;
	}

	public class CheogramConnection extends Connection implements XmppConnectionService.OnJingleRtpConnectionUpdate {
		protected Account account;
		protected Jid with;
		protected String sessionId = null;
		protected Stack<String> postDial = new Stack<>();
		protected Icon gatewayIcon;
		protected WeakReference<JingleRtpConnection> rtpConnection = null;

		CheogramConnection(Account account, Jid with, String postDialString) {
			super();
			this.account = account;
			this.with = with;

			gatewayIcon = Icon.createWithBitmap(xmppConnectionService.getAvatarService().get(
				account.getRoster().getContact(Jid.of(with.getDomain())),
				AvatarService.getSystemUiAvatarSize(xmppConnectionService),
				false
			));

			if (postDialString != null) {
				for (int i = postDialString.length() - 1; i >= 0; i--) {
					postDial.push("" + postDialString.charAt(i));
				}
			}

			setCallerDisplayName(
				account.getDisplayName(),
				TelecomManager.PRESENTATION_ALLOWED
			);
			setAudioModeIsVoip(true);
			setConnectionCapabilities(
				Connection.CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION
			);
		}

		public void setSessionId(final String sessionId) {
			this.sessionId = sessionId;
		}

		@Override
		public void onJingleRtpConnectionUpdate(final Account account, final Jid with, final String sessionId, final RtpEndUserState state) {
			if (sessionId == null || !sessionId.equals(this.sessionId)) return;
			if (rtpConnection == null) {
				this.with = with; // Store full JID of connection
				rtpConnection = xmppConnectionService.getJingleConnectionManager().findJingleRtpConnection(account, with, sessionId);
			}

			setStatusHints(new StatusHints(null, gatewayIcon, null));

			if (state == RtpEndUserState.FINDING_DEVICE) {
				setInitialized();
			} else if (state == RtpEndUserState.RINGING) {
				setDialing();
			} else if (state == RtpEndUserState.CONNECTED) {
				xmppConnectionService.setDiallerIntegrationActive(true);
				setActive();

				postDial();
			} else if (state == RtpEndUserState.DECLINED_OR_BUSY) {
				setDisconnected(new DisconnectCause(DisconnectCause.BUSY));
			} else if (state == RtpEndUserState.ENDED) {
				setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
			} else if (state == RtpEndUserState.RETRACTED) {
				setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
			} else if (RtpSessionActivity.END_CARD.contains(state)) {
				setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
			}
		}

		@Override
		public void onAudioDeviceChanged(AppRTCAudioManager.AudioDevice selectedAudioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
			switch(selectedAudioDevice) {
				case SPEAKER_PHONE:
					setAudioRoute(CallAudioState.ROUTE_SPEAKER);
				case WIRED_HEADSET:
					setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET);
				case EARPIECE:
					setAudioRoute(CallAudioState.ROUTE_EARPIECE);
				case BLUETOOTH:
					setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
				default:
					setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE);
			}
		}

		@Override
		public void onDisconnect() {
			if (rtpConnection == null || rtpConnection.get() == null) {
				xmppConnectionService.getJingleConnectionManager().retractSessionProposal(account, with.asBareJid());
			} else {
				rtpConnection.get().endCall();
			}
			destroy();
			xmppConnectionService.setDiallerIntegrationActive(false);
			xmppConnectionService.removeRtpConnectionUpdateListener(
				(XmppConnectionService.OnJingleRtpConnectionUpdate) this
			);
		}

		@Override
		public void onAbort() {
			onDisconnect();
		}

		@Override
		public void onPlayDtmfTone(char c) {
			rtpConnection.get().applyDtmfTone("" + c);
		}

		@Override
		public void onPostDialContinue(boolean c) {
			if (c) postDial();
		}

		protected void sleep(int ms) {
			try {
				Thread.sleep(ms);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}

		protected void postDial() {
			while (!postDial.empty()) {
				String next = postDial.pop();
				if (next.equals(";")) {
					Stack v = (Stack) postDial.clone();
					Collections.reverse(v);
					setPostDialWait(String.join("", v));
					return;
				} else if (next.equals(",")) {
					sleep(2000);
				} else {
					rtpConnection.get().applyDtmfTone(next);
					sleep(100);
				}
			}
		}
	}
}
