package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.viewpager.widget.PagerAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.databinding.CommandPageBinding;
import eu.siacs.conversations.databinding.CommandNoteBinding;
import eu.siacs.conversations.databinding.CommandResultFieldBinding;
import eu.siacs.conversations.databinding.CommandTextFieldBinding;
import eu.siacs.conversations.databinding.CommandWebviewBinding;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.JidHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.mam.MamReference;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import static eu.siacs.conversations.entities.Bookmark.printableValue;


public class Conversation extends AbstractEntity implements Blockable, Comparable<Conversation>, Conversational, AvatarService.Avatarable {
    public static final String TABLENAME = "conversations";

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_ARCHIVED = 1;

    public static final String NAME = "name";
    public static final String ACCOUNT = "accountUuid";
    public static final String CONTACT = "contactUuid";
    public static final String CONTACTJID = "contactJid";
    public static final String STATUS = "status";
    public static final String CREATED = "created";
    public static final String MODE = "mode";
    public static final String ATTRIBUTES = "attributes";

    public static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    public static final String ATTRIBUTE_ALWAYS_NOTIFY = "always_notify";
    public static final String ATTRIBUTE_LAST_CLEAR_HISTORY = "last_clear_history";
    public static final String ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS = "formerly_private_non_anonymous";
    public static final String ATTRIBUTE_PINNED_ON_TOP = "pinned_on_top";
    static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";
    static final String ATTRIBUTE_MEMBERS_ONLY = "members_only";
    static final String ATTRIBUTE_MODERATED = "moderated";
    static final String ATTRIBUTE_NON_ANONYMOUS = "non_anonymous";
    private static final String ATTRIBUTE_NEXT_MESSAGE = "next_message";
    private static final String ATTRIBUTE_NEXT_MESSAGE_TIMESTAMP = "next_message_timestamp";
    private static final String ATTRIBUTE_CRYPTO_TARGETS = "crypto_targets";
    private static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
    private static final String ATTRIBUTE_CORRECTING_MESSAGE = "correcting_message";
    protected final ArrayList<Message> messages = new ArrayList<>();
    public AtomicBoolean messagesLoaded = new AtomicBoolean(true);
    protected Account account = null;
    private String draftMessage;
    private final String name;
    private final String contactUuid;
    private final String accountUuid;
    private Jid contactJid;
    private int status;
    private final long created;
    private int mode;
    private JSONObject attributes;
    private Jid nextCounterpart;
    private transient MucOptions mucOptions = null;
    private boolean messagesLeftOnServer = true;
    private ChatState mOutgoingChatState = Config.DEFAULT_CHAT_STATE;
    private ChatState mIncomingChatState = Config.DEFAULT_CHAT_STATE;
    private String mFirstMamReference = null;
    protected int mCurrentTab = -1;
    protected ConversationPagerAdapter pagerAdapter = new ConversationPagerAdapter();

    public Conversation(final String name, final Account account, final Jid contactJid,
                        final int mode) {
        this(java.util.UUID.randomUUID().toString(), name, null, account
                        .getUuid(), contactJid, System.currentTimeMillis(),
                STATUS_AVAILABLE, mode, "");
        this.account = account;
    }

    public Conversation(final String uuid, final String name, final String contactUuid,
                        final String accountUuid, final Jid contactJid, final long created, final int status,
                        final int mode, final String attributes) {
        this.uuid = uuid;
        this.name = name;
        this.contactUuid = contactUuid;
        this.accountUuid = accountUuid;
        this.contactJid = contactJid;
        this.created = created;
        this.status = status;
        this.mode = mode;
        try {
            this.attributes = new JSONObject(attributes == null ? "" : attributes);
        } catch (JSONException e) {
            this.attributes = new JSONObject();
        }
    }

    public static Conversation fromCursor(Cursor cursor) {
        return new Conversation(cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(NAME)),
                cursor.getString(cursor.getColumnIndex(CONTACT)),
                cursor.getString(cursor.getColumnIndex(ACCOUNT)),
                JidHelper.parseOrFallbackToInvalid(cursor.getString(cursor.getColumnIndex(CONTACTJID))),
                cursor.getLong(cursor.getColumnIndex(CREATED)),
                cursor.getInt(cursor.getColumnIndex(STATUS)),
                cursor.getInt(cursor.getColumnIndex(MODE)),
                cursor.getString(cursor.getColumnIndex(ATTRIBUTES)));
    }

    public static Message getLatestMarkableMessage(final List<Message> messages, boolean isPrivateAndNonAnonymousMuc) {
        for (int i = messages.size() - 1; i >= 0; --i) {
            final Message message = messages.get(i);
            if (message.getStatus() <= Message.STATUS_RECEIVED
                    && (message.markable || isPrivateAndNonAnonymousMuc)
                    && !message.isPrivateMessage()) {
                return message;
            }
        }
        return null;
    }

    private static boolean suitableForOmemoByDefault(final Conversation conversation) {
        if (conversation.getJid().asBareJid().equals(Config.BUG_REPORTS)) {
            return false;
        }
        if (conversation.getContact().isOwnServer()) {
            return false;
        }
        final String contact = conversation.getJid().getDomain().toEscapedString();
        final String account = conversation.getAccount().getServer();
        if (Config.OMEMO_EXCEPTIONS.matchesContactDomain(contact) || Config.OMEMO_EXCEPTIONS.ACCOUNT_DOMAINS.contains(account)) {
            return false;
        }
        return conversation.isSingleOrPrivateAndNonAnonymous() || conversation.getBooleanAttribute(ATTRIBUTE_FORMERLY_PRIVATE_NON_ANONYMOUS, false);
    }

    public boolean hasMessagesLeftOnServer() {
        return messagesLeftOnServer;
    }

    public void setHasMessagesLeftOnServer(boolean value) {
        this.messagesLeftOnServer = value;
    }

    public Message getFirstUnreadMessage() {
        Message first = null;
        synchronized (this.messages) {
            for (int i = messages.size() - 1; i >= 0; --i) {
                if (messages.get(i).isRead()) {
                    return first;
                } else {
                    first = messages.get(i);
                }
            }
        }
        return first;
    }

    public String findMostRecentRemoteDisplayableId() {
        final boolean multi = mode == Conversation.MODE_MULTI;
        synchronized (this.messages) {
            for (final Message message : Lists.reverse(this.messages)) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    final String serverMsgId = message.getServerMsgId();
                    if (serverMsgId != null && multi) {
                        return serverMsgId;
                    }
                    return message.getRemoteMsgId();
                }
            }
        }
        return null;
    }

    public int countFailedDeliveries() {
        int count = 0;
        synchronized (this.messages) {
            for(final Message message : this.messages) {
                if (message.getStatus() == Message.STATUS_SEND_FAILED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public Message getLastEditableMessage() {
        synchronized (this.messages) {
            for (final Message message : Lists.reverse(this.messages)) {
                if (message.isEditable()) {
                    if (message.isGeoUri() || message.getType() != Message.TYPE_TEXT) {
                        return null;
                    }
                    return message;
                }
            }
        }
        return null;
    }


    public Message findUnsentMessageWithUuid(String uuid) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                final int s = message.getStatus();
                if ((s == Message.STATUS_UNSEND || s == Message.STATUS_WAITING) && message.getUuid().equals(uuid)) {
                    return message;
                }
            }
        }
        return null;
    }

    public void findWaitingMessages(OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (message.getStatus() == Message.STATUS_WAITING) {
                    results.add(message);
                }
            }
        }
        for (Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public void findUnreadMessagesAndCalls(OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (message.isRead()) {
                    continue;
                }
                results.add(message);
            }
        }
        for (final Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public Message findMessageWithFileAndUuid(final String uuid) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                final Transferable transferable = message.getTransferable();
                final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(message);
                if (message.getUuid().equals(uuid)
                        && message.getEncryption() != Message.ENCRYPTION_PGP
                        && (message.isFileOrImage() || message.treatAsDownloadable() || unInitiatedButKnownSize || (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING))) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findMessageWithUuid(final String uuid) {
        synchronized (this.messages) {
            for (final Message message : this.messages) {
                if (message.getUuid().equals(uuid)) {
                    return message;
                }
            }
        }
        return null;
    }

    public boolean markAsDeleted(final List<String> uuids) {
        boolean deleted = false;
        final PgpDecryptionService pgpDecryptionService = account.getPgpDecryptionService();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (uuids.contains(message.getUuid())) {
                    message.setDeleted(true);
                    deleted = true;
                    if (message.getEncryption() == Message.ENCRYPTION_PGP && pgpDecryptionService != null) {
                        pgpDecryptionService.discard(message);
                    }
                }
            }
        }
        return deleted;
    }

    public boolean markAsChanged(final List<DatabaseBackend.FilePathInfo> files) {
        boolean changed = false;
        final PgpDecryptionService pgpDecryptionService = account.getPgpDecryptionService();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                for (final DatabaseBackend.FilePathInfo file : files)
                    if (file.uuid.toString().equals(message.getUuid())) {
                        message.setDeleted(file.deleted);
                        changed = true;
                        if (file.deleted && message.getEncryption() == Message.ENCRYPTION_PGP && pgpDecryptionService != null) {
                            pgpDecryptionService.discard(message);
                        }
                    }
            }
        }
        return changed;
    }

    public void clearMessages() {
        synchronized (this.messages) {
            this.messages.clear();
        }
    }

    public boolean setIncomingChatState(ChatState state) {
        if (this.mIncomingChatState == state) {
            return false;
        }
        this.mIncomingChatState = state;
        return true;
    }

    public ChatState getIncomingChatState() {
        return this.mIncomingChatState;
    }

    public boolean setOutgoingChatState(ChatState state) {
        if (mode == MODE_SINGLE && !getContact().isSelf() || (isPrivateAndNonAnonymous() && getNextCounterpart() == null)) {
            if (this.mOutgoingChatState != state) {
                this.mOutgoingChatState = state;
                return true;
            }
        }
        return false;
    }

    public ChatState getOutgoingChatState() {
        return this.mOutgoingChatState;
    }

    public void trim() {
        synchronized (this.messages) {
            final int size = messages.size();
            final int maxsize = Config.PAGE_SIZE * Config.MAX_NUM_PAGES;
            if (size > maxsize) {
                List<Message> discards = this.messages.subList(0, size - maxsize);
                final PgpDecryptionService pgpDecryptionService = account.getPgpDecryptionService();
                if (pgpDecryptionService != null) {
                    pgpDecryptionService.discard(discards);
                }
                discards.clear();
                untieMessages();
            }
        }
    }

    public void findUnsentTextMessages(OnMessageFound onMessageFound) {
        final ArrayList<Message> results = new ArrayList<>();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if ((message.getType() == Message.TYPE_TEXT || message.hasFileOnRemoteHost()) && message.getStatus() == Message.STATUS_UNSEND) {
                    results.add(message);
                }
            }
        }
        for (Message result : results) {
            onMessageFound.onMessageFound(result);
        }
    }

    public Message findSentMessageWithUuidOrRemoteId(String id) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (id.equals(message.getUuid())
                        || (message.getStatus() >= Message.STATUS_SEND
                        && id.equals(message.getRemoteMsgId()))) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findMessageWithRemoteIdAndCounterpart(String id, Jid counterpart, boolean received, boolean carbon) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = messages.get(i);
                final Jid mcp = message.getCounterpart();
                if (mcp == null) {
                    continue;
                }
                if (mcp.equals(counterpart) && ((message.getStatus() == Message.STATUS_RECEIVED) == received)
                        && (carbon == message.isCarbon() || received)) {
                    final boolean idMatch = id.equals(message.getRemoteMsgId()) || message.remoteMsgIdMatchInEdit(id);
                    if (idMatch && !message.isFileOrImage() && !message.treatAsDownloadable()) {
                        return message;
                    } else {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public Message findSentMessageWithUuid(String id) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (id.equals(message.getUuid())) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findMessageWithRemoteId(String id, Jid counterpart) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (counterpart.equals(message.getCounterpart())
                        && (id.equals(message.getRemoteMsgId()) || id.equals(message.getUuid()))) {
                    return message;
                }
            }
        }
        return null;
    }

    public Message findMessageWithServerMsgId(String id) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (id != null && id.equals(message.getServerMsgId())) {
                    return message;
                }
            }
        }
        return null;
    }

    public boolean hasMessageWithCounterpart(Jid counterpart) {
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (counterpart.equals(message.getCounterpart())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void populateWithMessages(final List<Message> messages) {
        synchronized (this.messages) {
            messages.clear();
            messages.addAll(this.messages);
        }
        for (Iterator<Message> iterator = messages.iterator(); iterator.hasNext(); ) {
            if (iterator.next().wasMergedIntoPrevious()) {
                iterator.remove();
            }
        }
    }

    @Override
    public boolean isBlocked() {
        return getContact().isBlocked();
    }

    @Override
    public boolean isDomainBlocked() {
        return getContact().isDomainBlocked();
    }

    @Override
    public Jid getBlockedJid() {
        return getContact().getBlockedJid();
    }

    public int countMessages() {
        synchronized (this.messages) {
            return this.messages.size();
        }
    }

    public String getFirstMamReference() {
        return this.mFirstMamReference;
    }

    public void setFirstMamReference(String reference) {
        this.mFirstMamReference = reference;
    }

    public void setLastClearHistory(long time, String reference) {
        if (reference != null) {
            setAttribute(ATTRIBUTE_LAST_CLEAR_HISTORY, time + ":" + reference);
        } else {
            setAttribute(ATTRIBUTE_LAST_CLEAR_HISTORY, time);
        }
    }

    public MamReference getLastClearHistory() {
        return MamReference.fromAttribute(getAttribute(ATTRIBUTE_LAST_CLEAR_HISTORY));
    }

    public List<Jid> getAcceptedCryptoTargets() {
        if (mode == MODE_SINGLE) {
            return Collections.singletonList(getJid().asBareJid());
        } else {
            return getJidListAttribute(ATTRIBUTE_CRYPTO_TARGETS);
        }
    }

    public void setAcceptedCryptoTargets(List<Jid> acceptedTargets) {
        setAttribute(ATTRIBUTE_CRYPTO_TARGETS, acceptedTargets);
    }

    public boolean setCorrectingMessage(Message correctingMessage) {
        setAttribute(ATTRIBUTE_CORRECTING_MESSAGE, correctingMessage == null ? null : correctingMessage.getUuid());
        return correctingMessage == null && draftMessage != null;
    }

    public Message getCorrectingMessage() {
        final String uuid = getAttribute(ATTRIBUTE_CORRECTING_MESSAGE);
        return uuid == null ? null : findSentMessageWithUuid(uuid);
    }

    public boolean withSelf() {
        return getContact().isSelf();
    }

    @Override
    public int compareTo(@NonNull Conversation another) {
        return ComparisonChain.start()
                .compareFalseFirst(another.getBooleanAttribute(ATTRIBUTE_PINNED_ON_TOP, false), getBooleanAttribute(ATTRIBUTE_PINNED_ON_TOP, false))
                .compare(another.getSortableTime(), getSortableTime())
                .result();
    }

    private long getSortableTime() {
        Draft draft = getDraft();
        long messageTime = getLatestMessage().getTimeSent();
        if (draft == null) {
            return messageTime;
        } else {
            return Math.max(messageTime, draft.getTimestamp());
        }
    }

    public String getDraftMessage() {
        return draftMessage;
    }

    public void setDraftMessage(String draftMessage) {
        this.draftMessage = draftMessage;
    }

    public boolean isRead() {
        synchronized (this.messages) {
            for(final Message message : Lists.reverse(this.messages)) {
                if (message.isRead() && message.getType() == Message.TYPE_RTP_SESSION) {
                    continue;
                }
                return message.isRead();
            }
            return true;
        }
    }

    public List<Message> markRead(String upToUuid) {
        final List<Message> unread = new ArrayList<>();
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (!message.isRead()) {
                    message.markRead();
                    unread.add(message);
                }
                if (message.getUuid().equals(upToUuid)) {
                    return unread;
                }
            }
        }
        return unread;
    }

    public Message getLatestMessage() {
        synchronized (this.messages) {
            if (this.messages.size() == 0) {
                Message message = new Message(this, "", Message.ENCRYPTION_NONE);
                message.setType(Message.TYPE_STATUS);
                message.setTime(Math.max(getCreated(), getLastClearHistory().getTimestamp()));
                return message;
            } else {
                return this.messages.get(this.messages.size() - 1);
            }
        }
    }

    public @NonNull
    CharSequence getName() {
        if (getMode() == MODE_MULTI) {
            final String roomName = getMucOptions().getName();
            final String subject = getMucOptions().getSubject();
            final Bookmark bookmark = getBookmark();
            final String bookmarkName = bookmark != null ? bookmark.getBookmarkName() : null;
            if (printableValue(roomName)) {
                return roomName;
            } else if (printableValue(subject)) {
                return subject;
            } else if (printableValue(bookmarkName, false)) {
                return bookmarkName;
            } else {
                final String generatedName = getMucOptions().createNameFromParticipants();
                if (printableValue(generatedName)) {
                    return generatedName;
                } else {
                    return contactJid.getLocal() != null ? contactJid.getLocal() : contactJid;
                }
            }
        } else if ((QuickConversationsService.isConversations() || !Config.QUICKSY_DOMAIN.equals(contactJid.getDomain())) && isWithStranger()) {
            return contactJid;
        } else {
            return this.getContact().getDisplayName();
        }
    }

    public String getAccountUuid() {
        return this.accountUuid;
    }

    public Account getAccount() {
        return this.account;
    }

    public void setAccount(final Account account) {
        this.account = account;
    }

    public Contact getContact() {
        return this.account.getRoster().getContact(this.contactJid);
    }

    @Override
    public Jid getJid() {
        return this.contactJid;
    }

    public int getStatus() {
        return this.status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getCreated() {
        return this.created;
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(NAME, name);
        values.put(CONTACT, contactUuid);
        values.put(ACCOUNT, accountUuid);
        values.put(CONTACTJID, contactJid.toString());
        values.put(CREATED, created);
        values.put(STATUS, status);
        values.put(MODE, mode);
        synchronized (this.attributes) {
            values.put(ATTRIBUTES, attributes.toString());
        }
        return values;
    }

    public int getMode() {
        return this.mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    /**
     * short for is Private and Non-anonymous
     */
    public boolean isSingleOrPrivateAndNonAnonymous() {
        return mode == MODE_SINGLE || isPrivateAndNonAnonymous();
    }

    public boolean isPrivateAndNonAnonymous() {
        return getMucOptions().isPrivateAndNonAnonymous();
    }

    public synchronized MucOptions getMucOptions() {
        if (this.mucOptions == null) {
            this.mucOptions = new MucOptions(this);
        }
        return this.mucOptions;
    }

    public void resetMucOptions() {
        this.mucOptions = null;
    }

    public void setContactJid(final Jid jid) {
        this.contactJid = jid;
    }

    public Jid getNextCounterpart() {
        return this.nextCounterpart;
    }

    public void setNextCounterpart(Jid jid) {
        this.nextCounterpart = jid;
    }

    public int getNextEncryption() {
        if (!Config.supportOmemo() && !Config.supportOpenPgp()) {
            return Message.ENCRYPTION_NONE;
        }
        if (OmemoSetting.isAlways()) {
            return suitableForOmemoByDefault(this) ? Message.ENCRYPTION_AXOLOTL : Message.ENCRYPTION_NONE;
        }
        final int defaultEncryption;
        if (suitableForOmemoByDefault(this)) {
            defaultEncryption = OmemoSetting.getEncryption();
        } else {
            defaultEncryption = Message.ENCRYPTION_NONE;
        }
        int encryption = this.getIntAttribute(ATTRIBUTE_NEXT_ENCRYPTION, defaultEncryption);
        if (encryption == Message.ENCRYPTION_OTR || encryption < 0) {
            return defaultEncryption;
        } else {
            return encryption;
        }
    }

    public boolean setNextEncryption(int encryption) {
        return this.setAttribute(ATTRIBUTE_NEXT_ENCRYPTION, encryption);
    }

    public String getNextMessage() {
        final String nextMessage = getAttribute(ATTRIBUTE_NEXT_MESSAGE);
        return nextMessage == null ? "" : nextMessage;
    }

    public @Nullable
    Draft getDraft() {
        long timestamp = getLongAttribute(ATTRIBUTE_NEXT_MESSAGE_TIMESTAMP, 0);
        if (timestamp > getLatestMessage().getTimeSent()) {
            String message = getAttribute(ATTRIBUTE_NEXT_MESSAGE);
            if (!TextUtils.isEmpty(message) && timestamp != 0) {
                return new Draft(message, timestamp);
            }
        }
        return null;
    }

    public boolean setNextMessage(final String input) {
        final String message = input == null || input.trim().isEmpty() ? null : input;
        boolean changed = !getNextMessage().equals(message);
        this.setAttribute(ATTRIBUTE_NEXT_MESSAGE, message);
        if (changed) {
            this.setAttribute(ATTRIBUTE_NEXT_MESSAGE_TIMESTAMP, message == null ? 0 : System.currentTimeMillis());
        }
        return changed;
    }

    public Bookmark getBookmark() {
        return this.account.getBookmark(this.contactJid);
    }

    public Message findDuplicateMessage(Message message) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).similar(message)) {
                    return this.messages.get(i);
                }
            }
        }
        return null;
    }

    public boolean hasDuplicateMessage(Message message) {
        return findDuplicateMessage(message) != null;
    }

    public Message findSentMessageWithBody(String body) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                Message message = this.messages.get(i);
                if (message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_SEND) {
                    String otherBody;
                    if (message.hasFileOnRemoteHost()) {
                        otherBody = message.getFileParams().url;
                    } else {
                        otherBody = message.body;
                    }
                    if (otherBody != null && otherBody.equals(body)) {
                        return message;
                    }
                }
            }
            return null;
        }
    }

    public Message findRtpSession(final String sessionId, final int s) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = this.messages.get(i);
                if ((message.getStatus() == s) && (message.getType() == Message.TYPE_RTP_SESSION) && sessionId.equals(message.getRemoteMsgId())) {
                    return message;
                }
            }
        }
        return null;
    }

    public boolean possibleDuplicate(final String serverMsgId, final String remoteMsgId) {
        if (serverMsgId == null || remoteMsgId == null) {
            return false;
        }
        synchronized (this.messages) {
            for (Message message : this.messages) {
                if (serverMsgId.equals(message.getServerMsgId()) || remoteMsgId.equals(message.getRemoteMsgId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public MamReference getLastMessageTransmitted() {
        final MamReference lastClear = getLastClearHistory();
        MamReference lastReceived = new MamReference(0);
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = this.messages.get(i);
                if (message.isPrivateMessage()) {
                    continue; //it's unsafe to use private messages as anchor. They could be coming from user archive
                }
                if (message.getStatus() == Message.STATUS_RECEIVED || message.isCarbon() || message.getServerMsgId() != null) {
                    lastReceived = new MamReference(message.getTimeSent(), message.getServerMsgId());
                    break;
                }
            }
        }
        return MamReference.max(lastClear, lastReceived);
    }

    public void setMutedTill(long value) {
        this.setAttribute(ATTRIBUTE_MUTED_TILL, String.valueOf(value));
    }

    public boolean isMuted() {
        return System.currentTimeMillis() < this.getLongAttribute(ATTRIBUTE_MUTED_TILL, 0);
    }

    public boolean alwaysNotify() {
        return mode == MODE_SINGLE || getBooleanAttribute(ATTRIBUTE_ALWAYS_NOTIFY, Config.ALWAYS_NOTIFY_BY_DEFAULT || isPrivateAndNonAnonymous());
    }

    public boolean setAttribute(String key, boolean value) {
        return setAttribute(key, String.valueOf(value));
    }

    private boolean setAttribute(String key, long value) {
        return setAttribute(key, Long.toString(value));
    }

    private boolean setAttribute(String key, int value) {
        return setAttribute(key, String.valueOf(value));
    }

    public boolean setAttribute(String key, String value) {
        synchronized (this.attributes) {
            try {
                if (value == null) {
                    if (this.attributes.has(key)) {
                        this.attributes.remove(key);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    final String prev = this.attributes.optString(key, null);
                    this.attributes.put(key, value);
                    return !value.equals(prev);
                }
            } catch (JSONException e) {
                throw new AssertionError(e);
            }
        }
    }

    public boolean setAttribute(String key, List<Jid> jids) {
        JSONArray array = new JSONArray();
        for (Jid jid : jids) {
            array.put(jid.asBareJid().toString());
        }
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, array);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    public String getAttribute(String key) {
        synchronized (this.attributes) {
            return this.attributes.optString(key, null);
        }
    }

    private List<Jid> getJidListAttribute(String key) {
        ArrayList<Jid> list = new ArrayList<>();
        synchronized (this.attributes) {
            try {
                JSONArray array = this.attributes.getJSONArray(key);
                for (int i = 0; i < array.length(); ++i) {
                    try {
                        list.add(Jid.of(array.getString(i)));
                    } catch (IllegalArgumentException e) {
                        //ignored
                    }
                }
            } catch (JSONException e) {
                //ignored
            }
        }
        return list;
    }

    private int getIntAttribute(String key, int defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public long getLongAttribute(String key, long defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public boolean getBooleanAttribute(String key, boolean defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    public void add(Message message) {
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    public void prepend(int offset, Message message) {
        synchronized (this.messages) {
            this.messages.add(Math.min(offset, this.messages.size()), message);
        }
    }

    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
        account.getPgpDecryptionService().decrypt(messages);
    }

    public void expireOldMessages(long timestamp) {
        synchronized (this.messages) {
            for (ListIterator<Message> iterator = this.messages.listIterator(); iterator.hasNext(); ) {
                if (iterator.next().getTimeSent() < timestamp) {
                    iterator.remove();
                }
            }
            untieMessages();
        }
    }

    public void sort() {
        synchronized (this.messages) {
            Collections.sort(this.messages, (left, right) -> {
                if (left.getTimeSent() < right.getTimeSent()) {
                    return -1;
                } else if (left.getTimeSent() > right.getTimeSent()) {
                    return 1;
                } else {
                    return 0;
                }
            });
            untieMessages();
        }
    }

    private void untieMessages() {
        for (Message message : this.messages) {
            message.untie();
        }
    }

    public int unreadCount() {
        synchronized (this.messages) {
            int count = 0;
            for(final Message message : Lists.reverse(this.messages)) {
                if (message.isRead()) {
                    if (message.getType() == Message.TYPE_RTP_SESSION) {
                        continue;
                    }
                    return count;
                }
                ++count;
            }
            return count;
        }
    }

    public int receivedMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for (Message message : messages) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public int sentMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for (Message message : messages) {
                if (message.getStatus() != Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public boolean isWithStranger() {
        final Contact contact = getContact();
        return mode == MODE_SINGLE
                && !contact.isOwnServer()
                && !contact.showInContactList()
                && !contact.isSelf()
                && !(contact.getJid().isDomainJid() && JidHelper.isQuicksyDomain(contact.getJid()))
                && sentMessagesCount() == 0;
    }

    public int getReceivedMessagesCountSinceUuid(String uuid) {
        if (uuid == null) {
            return 0;
        }
        int count = 0;
        synchronized (this.messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                final Message message = messages.get(i);
                if (uuid.equals(message.getUuid())) {
                    return count;
                }
                if (message.getStatus() <= Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return 0;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(getName().toString());
    }

    @Override
    public String getAvatarName() {
        return getName().toString();
    }

    public void setCurrentTab(int tab) {
        mCurrentTab = tab;
    }

    public int getCurrentTab() {
        if (mCurrentTab >= 0) return mCurrentTab;

        if (!isRead() || getContact().resourceWhichSupport(Namespace.COMMANDS) == null) {
            return 0;
        }

        return 1;
    }

    public void startCommand(Element command, XmppConnectionService xmppConnectionService) {
        pagerAdapter.startCommand(command, xmppConnectionService);
    }

    public void setupViewPager(ViewPager pager, TabLayout tabs) {
        pagerAdapter.setupViewPager(pager, tabs);
    }

    public interface OnMessageFound {
        void onMessageFound(final Message message);
    }

    public static class Draft {
        private final String message;
        private final long timestamp;

        private Draft(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getMessage() {
            return message;
        }
    }

    public class ConversationPagerAdapter extends PagerAdapter {
        protected ViewPager mPager = null;
        protected TabLayout mTabs = null;
        ArrayList<CommandSession> sessions = new ArrayList<>();

        public void setupViewPager(ViewPager pager, TabLayout tabs) {
            mPager = pager;
            mTabs = tabs;
            pager.setAdapter(this);
            tabs.setupWithViewPager(mPager);
            pager.setCurrentItem(getCurrentTab());

            mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                public void onPageScrollStateChanged(int state) { }
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

                public void onPageSelected(int position) {
                    setCurrentTab(position);
                }
            });
        }

        public void startCommand(Element command, XmppConnectionService xmppConnectionService) {
            CommandSession session = new CommandSession(command.getAttribute("name"), xmppConnectionService);

            final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
            packet.setTo(command.getAttributeAsJid("jid"));
            final Element c = packet.addChild("command", Namespace.COMMANDS);
            c.setAttribute("node", command.getAttribute("node"));
            c.setAttribute("action", "execute");
            xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                mPager.post(() -> {
                    session.updateWithResponse(iq);
                });
            });

            sessions.add(session);
            notifyDataSetChanged();
            mPager.setCurrentItem(getCount() - 1);
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            if (position < 2) {
              return mPager.getChildAt(position);
            }

            CommandSession session = sessions.get(position-2);
            CommandPageBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_page, container, false);
            container.addView(binding.getRoot());
            binding.form.setAdapter(session);
            binding.done.setOnClickListener((button) -> {
                if (session.execute()) {
                    sessions.remove(session);
                    notifyDataSetChanged();
                }
            });

            session.setBinding(binding);
            return session;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, Object o) {
            if (position < 2) return;

            container.removeView(((CommandSession) o).getView());
        }

        @Override
        public int getItemPosition(Object o) {
            if (o == mPager.getChildAt(0)) return PagerAdapter.POSITION_UNCHANGED;
            if (o == mPager.getChildAt(1)) return PagerAdapter.POSITION_UNCHANGED;

            int pos = sessions.indexOf(o);
            if (pos < 0) return PagerAdapter.POSITION_NONE;
            return pos + 2;
        }

        @Override
        public int getCount() {
            int count = 2 + sessions.size();
            if (count > 2) {
                mTabs.setTabMode(TabLayout.MODE_SCROLLABLE);
            } else {
                mTabs.setTabMode(TabLayout.MODE_FIXED);
            }
            return count;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            if (view == o) return true;

            if (o instanceof CommandSession) {
                return ((CommandSession) o).getView() == view;
            }

            return false;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Conversation";
                case 1:
                    return "Commands";
                default:
                    CommandSession session = sessions.get(position-2);
                    if (session == null) return super.getPageTitle(position);
                    return session.getTitle();
            }
        }

        class CommandSession extends RecyclerView.Adapter<CommandSession.ViewHolder> {
            abstract class ViewHolder<T extends ViewDataBinding> extends RecyclerView.ViewHolder {
                protected T binding;

                public ViewHolder(T binding) {
                    super(binding.getRoot());
                    this.binding = binding;
                }

                abstract public void bind(Element el);
            }

            class ErrorViewHolder extends ViewHolder<CommandNoteBinding> {
                public ErrorViewHolder(CommandNoteBinding binding) { super(binding); }

                @Override
                public void bind(Element iq) {
                    binding.errorIcon.setVisibility(View.VISIBLE);

                    Element error = iq.findChild("error");
                    if (error == null) return;
                    String text = error.findChildContent("text", "urn:ietf:params:xml:ns:xmpp-stanzas");
                    if (text == null || text.equals("")) {
                        text = error.getChildren().get(0).getName();
                    }
                    binding.message.setText(text);
                }
            }

            class NoteViewHolder extends ViewHolder<CommandNoteBinding> {
                public NoteViewHolder(CommandNoteBinding binding) { super(binding); }

                @Override
                public void bind(Element note) {
                    binding.message.setText(note.getContent());

                    String type = note.getAttribute("type");
                    if (type != null && type.equals("error")) {
                        binding.errorIcon.setVisibility(View.VISIBLE);
                    }
                }
            }

            class ResultFieldViewHolder extends ViewHolder<CommandResultFieldBinding> {
                public ResultFieldViewHolder(CommandResultFieldBinding binding) { super(binding); }

                @Override
                public void bind(Element field) {
                    String label = field.getAttribute("label");
                    if (label == null) label = field.getAttribute("var");
                    if (label == null) {
                        binding.label.setVisibility(View.GONE);
                    } else {
                        binding.label.setVisibility(View.VISIBLE);
                        binding.label.setText(label);
                    }

                    String desc = field.findChildContent("desc", "jabber:x:data");
                    if (desc == null) {
                        binding.desc.setVisibility(View.GONE);
                    } else {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(desc);
                    }

                    ArrayAdapter<String> values = new ArrayAdapter<String>(binding.getRoot().getContext(), R.layout.simple_list_item);
                    for (Element el : field.getChildren()) {
                        if (el.getName().equals("value") && el.getNamespace().equals("jabber:x:data")) {
                            values.add(el.getContent());
                        }
                    }
                    binding.values.setAdapter(values);
                }
            }

            class TextFieldViewHolder extends ViewHolder<CommandTextFieldBinding> implements TextWatcher {
                public TextFieldViewHolder(CommandTextFieldBinding binding) {
                    super(binding);
                    binding.textinput.addTextChangedListener(this);
                }
                protected Element mValue = null;

                @Override
                public void bind(Element field) {
                    String label = field.getAttribute("label");
                    if (label == null) label = field.getAttribute("var");
                    if (label == null) label = "";
                    binding.textinputLayout.setHint(label);

                    String desc = field.findChildContent("desc", "jabber:x:data");
                    if (desc == null) {
                        binding.desc.setVisibility(View.GONE);
                    } else {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(desc);
                    }

                    mValue = field.findChild("value", "jabber:x:data");
                    if (mValue == null) {
                        mValue = field.addChild("value", "jabber:x:data");
                    }
                    binding.textinput.setText(mValue.getContent());
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (mValue == null) return;

                    mValue.setContent(s.toString());
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int count, int after) { }
            }

            class WebViewHolder extends ViewHolder<CommandWebviewBinding> {
                public WebViewHolder(CommandWebviewBinding binding) { super(binding); }

                @Override
                public void bind(Element oob) {
                    binding.webview.getSettings().setJavaScriptEnabled(true);
                    binding.webview.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            mTitle = view.getTitle();
                            ConversationPagerAdapter.this.notifyDataSetChanged();
                        }
                    });
                    binding.webview.loadUrl(oob.findChildContent("url", "jabber:x:oob"));
                }
            }

            final int TYPE_ERROR = 1;
            final int TYPE_NOTE = 2;
            final int TYPE_WEB = 3;
            final int TYPE_RESULT_FIELD = 4;
            final int TYPE_TEXT_FIELD = 5;

            protected String mTitle;
            protected CommandPageBinding mBinding = null;
            protected IqPacket response = null;
            protected Element responseElement = null;
            protected XmppConnectionService xmppConnectionService;

            CommandSession(String title, XmppConnectionService xmppConnectionService) {
                mTitle = title;
                this.xmppConnectionService = xmppConnectionService;
            }

            public String getTitle() {
                return mTitle;
            }

            public void updateWithResponse(IqPacket iq) {
                this.responseElement = null;
                this.response = iq;

                Element command = iq.findChild("command", "http://jabber.org/protocol/commands");
                if (iq.getType() == IqPacket.TYPE.RESULT && command != null) {
                    for (Element el : command.getChildren()) {
                        if (el.getName().equals("x") && el.getNamespace().equals("jabber:x:data")) {
                            String title = el.findChildContent("title", "jabber:x:data");
                            if (title != null) {
                                mTitle = title;
                                ConversationPagerAdapter.this.notifyDataSetChanged();
                            }
                            this.responseElement = el;
                            break;
                        }
                        if (el.getName().equals("x") && el.getNamespace().equals("jabber:x:oob")) {
                            String url = el.findChildContent("url", "jabber:x:oob");
                            if (url != null) {
                                String scheme = Uri.parse(url).getScheme();
                                if (scheme.equals("http") || scheme.equals("https")) {
                                    this.responseElement = el;
                                    break;
                                }
                            }
                        }
                        if (el.getName().equals("note") && el.getNamespace().equals("http://jabber.org/protocol/commands")) {
                            this.responseElement = el;
                            break;
                        }
                    }
                }

                notifyDataSetChanged();
            }

            @Override
            public int getItemCount() {
                if (response == null) return 0;
                if (response.getType() == IqPacket.TYPE.RESULT && responseElement.getNamespace().equals("jabber:x:data")) {
                    int i = 0;
                    for (Element el : responseElement.getChildren()) {
                        if (!el.getNamespace().equals("jabber:x:data")) continue;
                        if (el.getName().equals("title")) continue;
                        if (el.getName().equals("field")) {
                            String type = el.getAttribute("type");
                            if (type != null && type.equals("hidden")) continue;
                        }

                        i++;
                    }
                    return i;
                }
                return 1;
            }

            public Element getItem(int position) {
                if (response == null) return null;

                if (response.getType() == IqPacket.TYPE.RESULT) {
                    if (responseElement.getNamespace().equals("jabber:x:data")) {
                        int i = 0;
                        for (Element el : responseElement.getChildren()) {
                            if (!el.getNamespace().equals("jabber:x:data")) continue;
                            if (el.getName().equals("title")) continue;
                            if (el.getName().equals("field")) {
                                String type = el.getAttribute("type");
                                if (type != null && type.equals("hidden")) continue;
                            }

                            if (i < position) {
                                i++;
                                continue;
                            }

                            return el;
                        }
                    }
                }

                return responseElement == null ? response : responseElement;
            }

            @Override
            public int getItemViewType(int position) {
                if (response == null) return -1;

                if (response.getType() == IqPacket.TYPE.RESULT) {
                    Element item = getItem(position);
                    if (item.getName().equals("note")) return TYPE_NOTE;
                    if (item.getNamespace().equals("jabber:x:oob")) return TYPE_WEB;
                    if (item.getName().equals("instructions") && item.getNamespace().equals("jabber:x:data")) return TYPE_NOTE;
                    if (item.getName().equals("field") && item.getNamespace().equals("jabber:x:data")) {
                        String formType = responseElement.getAttribute("type");
                        String fieldType = item.getAttribute("type");
                        if (formType.equals("result") || fieldType.equals("fixed")) return TYPE_RESULT_FIELD;
                        if (formType.equals("form")) return TYPE_TEXT_FIELD;
                    }
                    return -1;
                } else {
                    return TYPE_ERROR;
                }
            }

            @Override
            public ViewHolder onCreateViewHolder(ViewGroup container, int viewType) {
                switch(viewType) {
                    case TYPE_ERROR: {
                        CommandNoteBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_note, container, false);
                        return new ErrorViewHolder(binding);
                    }
                    case TYPE_NOTE: {
                        CommandNoteBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_note, container, false);
                        return new NoteViewHolder(binding);
                    }
                    case TYPE_WEB: {
                        CommandWebviewBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_webview, container, false);
                        return new WebViewHolder(binding);
                    }
                    case TYPE_RESULT_FIELD: {
                        CommandResultFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_result_field, container, false);
                        return new ResultFieldViewHolder(binding);
                    }
                    case TYPE_TEXT_FIELD: {
                        CommandTextFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_text_field, container, false);
                        return new TextFieldViewHolder(binding);
                    }
                    default:
                        throw new IllegalArgumentException("Unknown viewType: " + viewType);
                }
            }

            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int position) {
                viewHolder.bind(getItem(position));
            }

            public View getView() {
                return mBinding.getRoot();
            }

            public boolean execute() {
                if (response == null || responseElement == null) return true;
                Element command = response.findChild("command", "http://jabber.org/protocol/commands");
                if (command == null) return true;
                String status = command.getAttribute("status");
                if (status == null || !status.equals("executing")) return true;
                if (!responseElement.getName().equals("x") || !responseElement.getNamespace().equals("jabber:x:data")) return true;
                String formType = responseElement.getAttribute("type");
                if (formType == null || !formType.equals("form")) return true;

                responseElement.setAttribute("type", "submit");

                final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
                packet.setTo(response.getFrom());
                final Element c = packet.addChild("command", Namespace.COMMANDS);
                c.setAttribute("node", command.getAttribute("node"));
                c.setAttribute("sessionid", command.getAttribute("sessionid"));
                c.setAttribute("action", "execute");
                c.addChild(responseElement);

                xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                    getView().post(() -> {
                        updateWithResponse(iq);
                    });
                });

                return false;
            }

            public void setBinding(CommandPageBinding b) {
                mBinding = b;
                mBinding.form.setLayoutManager(new LinearLayoutManager(mPager.getContext()) {
                    @Override
                    public boolean canScrollVertically() { return getItemCount() > 1; }
                });
            }
        }
    }
}
