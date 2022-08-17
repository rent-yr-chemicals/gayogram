package com.cheogram.android;

import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.http.AesGcmURL;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class BobTransfer implements Transferable {
	protected int status = Transferable.STATUS_OFFER;
	protected Message message;
	protected URI uri;
	protected XmppConnectionService xmppConnectionService;
	protected DownloadableFile file;

	public BobTransfer(Message message, XmppConnectionService xmppConnectionService) throws URISyntaxException {
		this.message = message;
		this.xmppConnectionService = xmppConnectionService;
		this.uri = new URI(message.getFileParams().url);
		setupFile();
	}

	private void setupFile() {
		final String reference = uri.getFragment();
		if (reference != null && AesGcmURL.IV_KEY.matcher(reference).matches()) {
			this.file = new DownloadableFile(xmppConnectionService.getCacheDir(), message.getUuid());
			this.file.setKeyAndIv(CryptoHelper.hexToBytes(reference));
			Log.d(Config.LOGTAG, "create temporary OMEMO encrypted file: " + this.file.getAbsolutePath() + "(" + message.getMimeType() + ")");
		} else {
			this.file = xmppConnectionService.getFileBackend().getFile(message, false);
		}
	}

	@Override
	public boolean start() {
		if (status == Transferable.STATUS_DOWNLOADING) return true;

		if (xmppConnectionService.hasInternetConnection()) {
			changeStatus(Transferable.STATUS_DOWNLOADING);

			IqPacket request = new IqPacket(IqPacket.TYPE.GET);
			request.setTo(message.getCounterpart());
			final Element dataq = request.addChild("data", "urn:xmpp:bob");
			dataq.setAttribute("cid", uri.getSchemeSpecificPart());
			xmppConnectionService.sendIqPacket(message.getConversation().getAccount(), request, (acct, packet) -> {
				final Element data = packet.findChild("data", "urn:xmpp:bob");
				if (packet.getType() == IqPacket.TYPE.ERROR || data == null) {
					Log.d(Config.LOGTAG, "BobTransfer failed: " + packet);
					xmppConnectionService.showErrorToastInUi(R.string.download_failed_file_not_found);
				} else {
					final String contentType = data.getAttribute("type");
					if (contentType != null) {
						final String fileExtension = MimeUtils.guessExtensionFromMimeType(contentType);
						if (fileExtension != null) {
							xmppConnectionService.getFileBackend().setupRelativeFilePath(message, String.format("%s.%s", message.getUuid(), fileExtension), contentType);
							Log.d(Config.LOGTAG, "rewriting name for bob based on content type");
							setupFile();
						}
					}

					try {
						file.getParentFile().mkdirs();
						if (!file.exists() && !file.createNewFile()) {
							throw new IOException(file.getAbsolutePath());
						}
						final OutputStream outputStream = AbstractConnectionManager.createOutputStream(file, false, false);
						outputStream.write(Base64.decode(data.getContent(), Base64.DEFAULT));
						outputStream.flush();
						outputStream.close();

						final boolean privateMessage = message.isPrivateMessage();
						message.setType(privateMessage ? Message.TYPE_PRIVATE_FILE : Message.TYPE_FILE);
						xmppConnectionService.getFileBackend().updateFileParams(message, uri.toString());
						xmppConnectionService.updateMessage(message);
					} catch (IOException e) {
						xmppConnectionService.showErrorToastInUi(R.string.download_failed_could_not_write_file);
					}
				}
				message.setTransferable(null);
				xmppConnectionService.updateConversationUi();
			});
			return true;
		} else {
			return false;
		}
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public int getProgress() {
		return 0;
	}

	@Override
	public Long getFileSize() {
		return null;
	}

	@Override
	public void cancel() {
		// No real way to cancel an iq in process...
		changeStatus(Transferable.STATUS_CANCELLED);
		message.setTransferable(null);
	}

	protected void changeStatus(int newStatus) {
		status = newStatus;
		xmppConnectionService.updateConversationUi();
	}
}
