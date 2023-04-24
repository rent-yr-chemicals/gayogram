// Based on GPLv3 code from deltachat-android
// https://github.com/deltachat/deltachat-android/blob/master/src/org/thoughtcrime/securesms/WebViewActivity.java
// https://github.com/deltachat/deltachat-android/blob/master/src/org/thoughtcrime/securesms/WebxdcActivity.java
package com.cheogram.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import io.ipfs.cid.Cid;

import java.lang.ref.WeakReference;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONObject;
import org.json.JSONException;

import org.tomlj.Toml;
import org.tomlj.TomlTable;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.WebxdcPageBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Consumer;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;

public class WebxdcPage implements ConversationPage {
	protected XmppConnectionService xmppConnectionService;
	protected WebxdcPageBinding binding = null;
	protected ZipFile zip = null;
	protected TomlTable manifest = null;
	protected String baseUrl;
	protected Message source;
	protected WebxdcUpdate lastUpdate = null;

	public WebxdcPage(Cid cid, Message source, XmppConnectionService xmppConnectionService) {
		this.xmppConnectionService = xmppConnectionService;
		this.source = source;
		File f = xmppConnectionService.getFileForCid(cid);
		try {
			if (f != null) zip = new ZipFile(xmppConnectionService.getFileForCid(cid));
			final ZipEntry manifestEntry = zip.getEntry("manifest.toml");
			if (manifestEntry != null) {
				manifest = Toml.parse(zip.getInputStream(manifestEntry));
			}
		} catch (final IOException e) {
			Log.w(Config.LOGTAG, "WebxdcPage: " + e);
		}

		// ids in the subdomain makes sure, different apps using same files do not share the same cache entry
		// (WebView may use a global cache shared across objects).
		// (a random-id would also work, but would need maintenance and does not add benefits as we regard the file-part interceptRequest() only,
		// also a random-id is not that useful for debugging)
		baseUrl = "https://" + source.getUuid() + ".localhost";
	}

	public String getTitle() {
		String title = manifest == null ? null : manifest.getString("name");
		if (lastUpdate != null && lastUpdate.getDocument() != null) {
			if (title == null) {
				title = lastUpdate.getDocument();
			} else {
				title += ": " + lastUpdate.getDocument();
			}
		}
		return title == null ? "ChatApp" : title;
	}

	public String getNode() {
		return "webxdc\0" + source.getUuid();
	}

	public boolean openUri(Uri uri) {
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		xmppConnectionService.startActivity(intent);
		return true;
	}

	protected WebResourceResponse interceptRequest(String rawUrl) {
		Log.i(Config.LOGTAG, "interceptRequest: " + rawUrl);
		WebResourceResponse res = null;
		try {
			if (zip == null) {
				throw new Exception("no zip found");
			}
			if (rawUrl == null) {
				throw new Exception("no url specified");
			}
			String path = Uri.parse(rawUrl).getPath();
			if (path.equalsIgnoreCase("/webxdc.js")) {
				InputStream targetStream = xmppConnectionService.getResources().openRawResource(R.raw.webxdc);
				res = new WebResourceResponse("text/javascript", "UTF-8", targetStream);
			} else {
				ZipEntry entry = zip.getEntry(path.substring(1));
				if (entry == null) {
					throw new Exception("\"" + path + "\" not found");
				}
				String mimeType = MimeUtils.guessFromPath(path);
				String encoding = mimeType.startsWith("text/") ? "UTF-8" : null;
				res = new WebResourceResponse(mimeType, encoding, zip.getInputStream(entry));
			}
		} catch (Exception e) {
			e.printStackTrace();
			InputStream targetStream = new ByteArrayInputStream(("Webxdc Request Error: " + e.getMessage()).getBytes());
			res = new WebResourceResponse("text/plain", "UTF-8", targetStream);
		}

		if (res != null) {
			Map<String, String> headers = new HashMap<>();
			headers.put("Content-Security-Policy",
					"default-src 'self'; "
				+ "style-src 'self' 'unsafe-inline' blob: ; "
				+ "font-src 'self' data: blob: ; "
				+ "script-src 'self' 'unsafe-inline' 'unsafe-eval' blob: ; "
				+ "connect-src 'self' data: blob: ; "
				+ "img-src 'self' data: blob: ; "
				+ "webrtc 'block' ; "
			);
			headers.put("X-DNS-Prefetch-Control", "off");
			res.setResponseHeaders(headers);
		}
		return res;
	}

	public View inflateUi(Context context, Consumer<ConversationPage> remover) {
		if (binding != null) {
			binding.webview.loadUrl("javascript:__webxdcUpdate();");
			return getView();
		}

		binding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.webxdc_page, null, false);
		binding.webview.setWebViewClient(new WebViewClient() {
			// `shouldOverrideUrlLoading()` is called when the user clicks a URL,
			// returning `true` causes the WebView to abort loading the URL,
			// returning `false` causes the WebView to continue loading the URL as usual.
			// the method is not called for POST request nor for on-page-links.
			//
			// nb: from API 24, `shouldOverrideUrlLoading(String)` is deprecated and
			// `shouldOverrideUrlLoading(WebResourceRequest)` shall be used.
			// the new one has the same functionality, and the old one still exist,
			// so, to support all systems, for now, using the old one seems to be the simplest way.
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				if (url != null) {
					Uri uri = Uri.parse(url);
					switch (uri.getScheme()) {
						case "http":
						case "https":
						case "mailto":
						case "xmpp":
							return openUri(uri);
					}
				}
				// by returning `true`, we also abort loading other URLs in our WebView;
				// eg. that might be weird or internal protocols.
				// if we come over really useful things, we should allow that explicitly.
				return true;
			}

			@Override
			@SuppressWarnings("deprecation")
			public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
				WebResourceResponse res = interceptRequest(url);
				if (res!=null) {
					return res;
				}
				return super.shouldInterceptRequest(view, url);
			}

			@Override
			@RequiresApi(21)
			public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
				WebResourceResponse res = interceptRequest(request.getUrl().toString());
				if (res!=null) {
					return res;
				}
				return super.shouldInterceptRequest(view, request);
			}
		});

		// disable "safe browsing" as this has privacy issues,
		// eg. at least false positives are sent to the "Safe Browsing Lookup API".
		// as all URLs opened in the WebView are local anyway,
		// "safe browsing" will never be able to report issues, so it can be disabled.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			binding.webview.getSettings().setSafeBrowsingEnabled(false);
		}

		WebSettings webSettings = binding.webview.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setAllowFileAccess(false);
		webSettings.setBlockNetworkLoads(true);
		webSettings.setAllowContentAccess(false);
		webSettings.setGeolocationEnabled(false);
		webSettings.setAllowFileAccessFromFileURLs(false);
		webSettings.setAllowUniversalAccessFromFileURLs(false);
		webSettings.setDatabaseEnabled(true);
		webSettings.setDomStorageEnabled(true);
		binding.webview.setNetworkAvailable(false); // this does not block network but sets `window.navigator.isOnline` in js land
		binding.webview.addJavascriptInterface(new InternalJSApi(), "InternalJSApi");

		binding.webview.loadUrl(baseUrl + "/index.html");

		binding.actions.setAdapter(new ArrayAdapter<String>(context, R.layout.simple_list_item, new String[]{"Close"}) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View v = super.getView(position, convertView, parent);
				TextView tv = (TextView) v.findViewById(android.R.id.text1);
				tv.setGravity(Gravity.CENTER);
				tv.setTextColor(ContextCompat.getColor(context, R.color.white));
				tv.setBackgroundColor(UIHelper.getColorForName(getItem(position)));
				return v;
			}
		});
		binding.actions.setOnItemClickListener((parent, v, pos, id) -> {
			remover.accept(WebxdcPage.this);
		});

		return getView();
	}

	public View getView() {
		if (binding == null) return null;
		return binding.getRoot();
	}

	public void refresh() {
		binding.webview.post(() -> binding.webview.loadUrl("javascript:__webxdcUpdate();"));
	}

	protected Jid selfJid() {
		final Conversation conversation = (Conversation) source.getConversation();
		if (conversation.getMode() == Conversation.MODE_MULTI && !conversation.getMucOptions().nonanonymous()) {
			return conversation.getMucOptions().getSelf().getFullJid();
		} else {
			return source.getConversation().getAccount().getJid().asBareJid();
		}
	}

	protected class InternalJSApi {
		@JavascriptInterface
		public String selfAddr() {
			return "xmpp:" + Uri.encode(selfJid().toEscapedString(), "@/+");
		}

		@JavascriptInterface
		public String selfName() {
			final Conversation conversation = (Conversation) source.getConversation();
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				return conversation.getMucOptions().getActualNick();
			} else {
				return conversation.getAccount().getDisplayName();
			}
		}

		@JavascriptInterface
		public boolean sendStatusUpdate(String paramS, String descr) {
			JSONObject params = new JSONObject();
			try {
				params = new JSONObject(paramS);
			} catch (final JSONException e) {
				Log.w(Config.LOGTAG, "WebxdcPage sendStatusUpdate invalid JSON: " + e);
			}
			String payload = null;
			Message message = new Message(source.getConversation(), descr, source.getEncryption());
			message.addPayload(new Element("store", "urn:xmpp:hints"));
			Element webxdc = new Element("x", "urn:xmpp:webxdc:0");
			message.addPayload(webxdc);
			if (params.has("payload")) {
				payload = JSONObject.wrap(params.opt("payload")).toString();
				webxdc.addChild("json", "urn:xmpp:json:0").setContent(payload);
			}
			if (params.has("document")) {
				webxdc.addChild("document").setContent(params.optString("document", null));
			}
			if (params.has("summary")) {
				webxdc.addChild("summary").setContent(params.optString("summary", null));
			}
			message.setBody(params.optString("info", null));
			message.setThread(source.getThread());
			if (source.isPrivateMessage()) {
				Message.configurePrivateMessage(message, source.getCounterpart());
			}
			xmppConnectionService.sendMessage(message);
			xmppConnectionService.insertWebxdcUpdate(new WebxdcUpdate(
				(Conversation) message.getConversation(),
				message.getUuid(),
				selfJid(),
				message.getThread(),
				params.optString("info", null),
				params.optString("document", null),
				params.optString("summary", null),
				payload
			));
			binding.webview.post(() -> binding.webview.loadUrl("javascript:__webxdcUpdate();"));
			return true;
		}

		@JavascriptInterface
		public String getStatusUpdates(long lastKnownSerial) {
			StringBuilder builder = new StringBuilder("[");
			String sep = "";
			for (WebxdcUpdate update : xmppConnectionService.findWebxdcUpdates(source, lastKnownSerial)) {
				lastUpdate = update;
				builder.append(sep);
				builder.append(update.toString());
				sep = ",";
			}
			builder.append("]");
			return builder.toString();
		}
	}
}
