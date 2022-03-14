package com.cheogram.android.test;

import java.util.concurrent.TimeoutException;
import java.lang.Thread;
import java.util.Arrays;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.cleanstatusbar.CleanStatusBar;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.entities.TransferablePlaceholder;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.test.R;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

@RunWith(AndroidJUnit4.class)
public class ScreenshotTest {

	static String pkg = InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
	static XmppConnectionService xmppConnectionService;
	static Account account;

	@ClassRule
	public static final LocaleTestRule localeTestRule = new LocaleTestRule();

	@ClassRule
	public static final ServiceTestRule xmppServiceRule = new ServiceTestRule();

	@BeforeClass
	public static void setup() throws TimeoutException {
		CleanStatusBar.enableWithDefaults();

		Intent intent = new Intent(ApplicationProvider.getApplicationContext(), XmppConnectionService.class);
		intent.setAction("ui");
		xmppConnectionService = ((XmppConnectionBinder) xmppServiceRule.bindService(intent)).getService();
		account = xmppConnectionService.findAccountByJid(Jid.of("carrot@chaosah.hereva"));
		if (account == null) {
			account = new Account(
				Jid.of("carrot@chaosah.hereva"),
				"orangeandfurry"
			);
			xmppConnectionService.createAccount(account);
		}

		Uri avatarUri = Uri.parse("android.resource://" + pkg + "/" + String.valueOf(R.drawable.carrot));
		final Avatar avatar = xmppConnectionService.getFileBackend().getPepAvatar(avatarUri, 192, Bitmap.CompressFormat.WEBP);
		xmppConnectionService.getFileBackend().save(avatar);
		account.setAvatar(avatar.getFilename());

		Contact cheogram = account.getRoster().getContact(Jid.of("cheogram.com"));
		cheogram.setOption(Contact.Options.IN_ROSTER);
		cheogram.setPhotoUri("android.resource://" + pkg + "/" + String.valueOf(R.drawable.cheogram));
		Presence cheogramPresence = Presence.parse(null, null, "");
		IqPacket discoPacket = new IqPacket(IqPacket.TYPE.RESULT);
		Element query = discoPacket.addChild("query", "http://jabber.org/protocol/disco#info");
		Element identity = query.addChild("identity");
		identity.setAttribute("category", "gateway");
		identity.setAttribute("type", "pstn");
		cheogramPresence.setServiceDiscoveryResult(new ServiceDiscoveryResult(discoPacket));
		cheogram.updatePresence("gw", cheogramPresence);
	}

	@AfterClass
	public static void teardown() {
		CleanStatusBar.disable();
	}

	@Test
	public void testConversation() throws FileBackend.FileCopyException, InterruptedException {
		Conversation conversation = xmppConnectionService.findOrCreateConversation(account, Jid.of("+15550737737@cheogram.com"), false, false);
		conversation.getContact().setOption(Contact.Options.IN_ROSTER);
		conversation.getContact().setSystemName("Pepper");
		conversation.getContact().setPhotoUri("android.resource://" + pkg + "/" + String.valueOf(R.drawable.pepper));

		Message voicemail = new Message(conversation, "", 0, Message.STATUS_RECEIVED);
		voicemail.setOob("https://example.com/thing.mp3");
		voicemail.setFileParams(new Message.FileParams("https://example.com/thing.mp3|5000|0|0|10000"));
		voicemail.setType(Message.TYPE_FILE);
		voicemail.setSubject("Voicemail Recording");

		Message transcript = new Message(conversation, "Where are you?", 0, Message.STATUS_RECEIVED);
		transcript.setSubject("Voicemail Transcription");

		Message picture = new Message(conversation, "", 0, Message.STATUS_SEND_RECEIVED);
		picture.setOob("https://example.com/thing.webp");
		picture.setType(Message.TYPE_FILE);
		xmppConnectionService.getFileBackend().copyFileToPrivateStorage(
			picture,
			Uri.parse("android.resource://" + pkg + "/" + String.valueOf(R.drawable.komona)),
			"image/webp"
		);
		xmppConnectionService.getFileBackend().updateFileParams(picture);

		conversation.addAll(0, Arrays.asList(
			voicemail,
			transcript,
			new Message(conversation, "Meow", 0, Message.STATUS_SEND_RECEIVED),
			picture,
			new Message(conversation, "👍", 0, Message.STATUS_RECEIVED)
		));

		ActivityScenario scenario = ActivityScenario.launch(ConversationsActivity.class);
		scenario.onActivity((Activity activity) -> {
			((ConversationsActivity) activity).switchToConversation(conversation);
		});
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
		Thread.sleep(100); // ImageView not paited yet after waitForIdleSync
		Screengrab.screenshot("conversation");
	}

	@Test
	public void testStartConversation() throws InterruptedException {
		ActivityScenario scenario = ActivityScenario.launch(StartConversationActivity.class);
		InstrumentationRegistry.getInstrumentation().waitForIdleSync();
		Thread.sleep(100); // ImageView not paited yet after waitForIdleSync
		Screengrab.screenshot("startConversation");
	}
}
