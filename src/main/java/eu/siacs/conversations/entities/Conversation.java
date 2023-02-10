package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.webkit.JavascriptInterface;
import android.webkit.WebMessage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.viewpager.widget.PagerAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewpager.widget.ViewPager;

import com.caverock.androidsvg.SVG;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.base.Optional;
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
import java.util.stream.Collectors;
import java.util.Timer;
import java.util.TimerTask;

import me.saket.bettermovementmethod.BetterLinkMovementMethod;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.databinding.CommandButtonGridFieldBinding;
import eu.siacs.conversations.databinding.CommandCheckboxFieldBinding;
import eu.siacs.conversations.databinding.CommandItemCardBinding;
import eu.siacs.conversations.databinding.CommandNoteBinding;
import eu.siacs.conversations.databinding.CommandPageBinding;
import eu.siacs.conversations.databinding.CommandProgressBarBinding;
import eu.siacs.conversations.databinding.CommandRadioEditFieldBinding;
import eu.siacs.conversations.databinding.CommandResultCellBinding;
import eu.siacs.conversations.databinding.CommandResultFieldBinding;
import eu.siacs.conversations.databinding.CommandSearchListFieldBinding;
import eu.siacs.conversations.databinding.CommandSpinnerFieldBinding;
import eu.siacs.conversations.databinding.CommandTextFieldBinding;
import eu.siacs.conversations.databinding.CommandWebviewBinding;
import eu.siacs.conversations.databinding.DialogQuickeditBinding;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.text.FixedURLSpan;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.JidHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Option;
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
    protected Element thread = null;
    protected boolean lockThread = false;
    protected boolean userSelectedThread = false;

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
            Message m = iterator.next();
            if (m.wasMergedIntoPrevious() || (getLockThread() && (m.getThread() == null || !m.getThread().getContent().equals(getThread().getContent())))) {
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
        long messageTime = getLatestMessage().getTimeReceived();
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

    public Element getThread() {
        return this.thread;
    }

    public void setThread(Element thread) {
        this.thread = thread;
    }

    public void setLockThread(boolean flag) {
        this.lockThread = flag;
        if (flag) setUserSelectedThread(true);
    }

    public boolean getLockThread() {
        return this.lockThread;
    }

    public void setUserSelectedThread(boolean flag) {
        this.userSelectedThread = flag;
    }

    public boolean getUserSelectedThread() {
        return this.userSelectedThread;
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
                message.setTimeReceived(Math.max(getCreated(), getLastClearHistory().getTimestamp()));
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

    public boolean canInferPresence() {
        final Contact contact = getContact();
        if (contact != null && contact.canInferPresence()) return true;
        return sentMessagesCount() > 0;
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

    public void showViewPager() {
        pagerAdapter.show();
    }

    public void hideViewPager() {
        pagerAdapter.hide();
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
        ArrayList<CommandSession> sessions = null;
        protected View page1 = null;
        protected View page2 = null;

        public void setupViewPager(ViewPager pager, TabLayout tabs) {
            mPager = pager;
            mTabs = tabs;

            if (mPager == null) return;
            if (sessions != null) show();

            page1 = pager.getChildAt(0) == null ? page1 : pager.getChildAt(0);
            page2 = pager.getChildAt(1) == null ? page2 : pager.getChildAt(1);
            pager.setAdapter(this);
            tabs.setupWithViewPager(mPager);
            pager.post(() -> pager.setCurrentItem(getCurrentTab()));

            mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                public void onPageScrollStateChanged(int state) { }
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

                public void onPageSelected(int position) {
                    setCurrentTab(position);
                }
            });
        }

        public void show() {
            if (sessions == null) {
                sessions = new ArrayList<>();
                notifyDataSetChanged();
            }
            if (mTabs != null) mTabs.setVisibility(View.VISIBLE);
        }

        public void hide() {
            if (sessions != null && !sessions.isEmpty()) return; // Do not hide during active session
            if (mPager != null) mPager.setCurrentItem(0);
            if (mTabs != null) mTabs.setVisibility(View.GONE);
            sessions = null;
            notifyDataSetChanged();
        }

        public void startCommand(Element command, XmppConnectionService xmppConnectionService) {
            show();
            CommandSession session = new CommandSession(command.getAttribute("name"), command.getAttribute("node"), xmppConnectionService);

            final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
            packet.setTo(command.getAttributeAsJid("jid"));
            final Element c = packet.addChild("command", Namespace.COMMANDS);
            c.setAttribute("node", command.getAttribute("node"));
            c.setAttribute("action", "execute");
            View v = mPager;
            xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                v.post(() -> {
                    session.updateWithResponse(iq);
                });
            });

            sessions.add(session);
            notifyDataSetChanged();
            if (mPager != null) mPager.setCurrentItem(getCount() - 1);
        }

        public void removeSession(CommandSession session) {
            sessions.remove(session);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            if (position == 0) {
                if (page1.getParent() == null) container.addView(page1);
                return page1;
            }
            if (position == 1) {
                if (page2.getParent() == null) container.addView(page2);
                return page2;
            }

            CommandSession session = sessions.get(position-2);
            CommandPageBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_page, container, false);
            container.addView(binding.getRoot());
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
            if (mPager != null) {
                if (o == page1) return PagerAdapter.POSITION_UNCHANGED;
                if (o == page2) return PagerAdapter.POSITION_UNCHANGED;
            }

            int pos = sessions == null ? -1 : sessions.indexOf(o);
            if (pos < 0) return PagerAdapter.POSITION_NONE;
            return pos + 2;
        }

        @Override
        public int getCount() {
            if (sessions == null) return 1;

            int count = 2 + sessions.size();
            if (mTabs == null) return count;

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

                abstract public void bind(Item el);

                protected void setTextOrHide(TextView v, Optional<String> s) {
                    if (s == null || !s.isPresent()) {
                        v.setVisibility(View.GONE);
                    } else {
                        v.setVisibility(View.VISIBLE);
                        v.setText(s.get());
                    }
                }

                protected void setupInputType(Element field, TextView textinput, TextInputLayout layout) {
                    int flags = 0;
                    if (layout != null) layout.setEndIconMode(TextInputLayout.END_ICON_NONE);
                    textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);

                    String type = field.getAttribute("type");
                    if (type != null) {
                        if (type.equals("text-multi") || type.equals("jid-multi")) {
                            flags |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                        }

                        textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);

                        if (type.equals("jid-single") || type.equals("jid-multi")) {
                            textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                        }

                        if (type.equals("text-private")) {
                            textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                            if (layout != null) layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
                        }
                    }

                    Element validate = field.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    if (validate == null) return;
                    String datatype = validate.getAttribute("datatype");
                    if (datatype == null) return;

                    if (datatype.equals("xs:integer") || datatype.equals("xs:int") || datatype.equals("xs:long") || datatype.equals("xs:short") || datatype.equals("xs:byte")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    }

                    if (datatype.equals("xs:decimal") || datatype.equals("xs:double")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    }

                    if (datatype.equals("xs:date")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
                    }

                    if (datatype.equals("xs:dateTime")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL);
                    }

                    if (datatype.equals("xs:time")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
                    }

                    if (datatype.equals("xs:anyURI")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                    }

                    if (datatype.equals("html:tel")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_PHONE);
                    }

                    if (datatype.equals("html:email")) {
                        textinput.setInputType(flags | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                    }
                }
            }

            class ErrorViewHolder extends ViewHolder<CommandNoteBinding> {
                public ErrorViewHolder(CommandNoteBinding binding) { super(binding); }

                @Override
                public void bind(Item iq) {
                    binding.errorIcon.setVisibility(View.VISIBLE);

                    Element error = iq.el.findChild("error");
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
                public void bind(Item note) {
                    binding.message.setText(note.el.getContent());

                    String type = note.el.getAttribute("type");
                    if (type != null && type.equals("error")) {
                        binding.errorIcon.setVisibility(View.VISIBLE);
                    }
                }
            }

            class ResultFieldViewHolder extends ViewHolder<CommandResultFieldBinding> {
                public ResultFieldViewHolder(CommandResultFieldBinding binding) { super(binding); }

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    ArrayAdapter<String> values = new ArrayAdapter<String>(binding.getRoot().getContext(), R.layout.simple_list_item);
                    for (Element el : field.el.getChildren()) {
                        if (el.getName().equals("value") && el.getNamespace().equals("jabber:x:data")) {
                            values.add(el.getContent());
                        }
                    }
                    binding.values.setAdapter(values);

                    if (field.getType().equals(Optional.of("jid-single")) || field.getType().equals(Optional.of("jid-multi"))) {
                        binding.values.setOnItemClickListener((arg0, arg1, pos, id) -> {
                            new FixedURLSpan("xmpp:" + Jid.ofEscaped(values.getItem(pos)).toEscapedString()).onClick(binding.values);
                        });
                    }

                    binding.values.setOnItemLongClickListener((arg0, arg1, pos, id) -> {
                        if (ShareUtil.copyTextToClipboard(binding.getRoot().getContext(), values.getItem(pos), R.string.message)) {
                            Toast.makeText(binding.getRoot().getContext(), R.string.message_copied_to_clipboard, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                }
            }

            class ResultCellViewHolder extends ViewHolder<CommandResultCellBinding> {
                public ResultCellViewHolder(CommandResultCellBinding binding) { super(binding); }

                @Override
                public void bind(Item item) {
                    Cell cell = (Cell) item;

                    if (cell.el == null) {
                        binding.text.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Subhead);
                        setTextOrHide(binding.text, cell.reported.getLabel());
                    } else {
                        String value = cell.el.findChildContent("value", "jabber:x:data");
                        SpannableStringBuilder text = new SpannableStringBuilder(value == null ? "" : value);
                        if (cell.reported.getType().equals(Optional.of("jid-single"))) {
                            text.setSpan(new FixedURLSpan("xmpp:" + Jid.ofEscaped(text.toString()).toEscapedString()), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }

                        binding.text.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Body1);
                        binding.text.setText(text);

                        BetterLinkMovementMethod method = BetterLinkMovementMethod.newInstance();
                        method.setOnLinkLongClickListener((tv, url) -> {
                            tv.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
                            ShareUtil.copyLinkToClipboard(binding.getRoot().getContext(), url);
                            return true;
                        });
                        binding.text.setMovementMethod(method);
                    }
                }
            }

            class ItemCardViewHolder extends ViewHolder<CommandItemCardBinding> {
                public ItemCardViewHolder(CommandItemCardBinding binding) { super(binding); }

                @Override
                public void bind(Item item) {
                    for (Field field : reported) {
                        CommandResultFieldBinding row = DataBindingUtil.inflate(LayoutInflater.from(binding.getRoot().getContext()), R.layout.command_result_field, binding.fields, false);
                        GridLayout.LayoutParams param = new GridLayout.LayoutParams();
                        param.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f);
                        param.width = 0;
                        row.getRoot().setLayoutParams(param);
                        binding.fields.addView(row.getRoot());
                        for (Element el : item.el.getChildren()) {
                            if (el.getName().equals("field") && el.getNamespace().equals("jabber:x:data") && el.getAttribute("var") != null && el.getAttribute("var").equals(field.getVar())) {
                                for (String label : field.getLabel().asSet()) {
                                    el.setAttribute("label", label);
                                }
                                for (String desc : field.getDesc().asSet()) {
                                    el.setAttribute("desc", desc);
                                }
                                for (String type : field.getType().asSet()) {
                                    el.setAttribute("type", type);
                                }
                                Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                                if (validate != null) el.addChild(validate);
                                new ResultFieldViewHolder(row).bind(new Field(eu.siacs.conversations.xmpp.forms.Field.parse(el), -1));
                            }
                        }
                    }
                }
            }

            class CheckboxFieldViewHolder extends ViewHolder<CommandCheckboxFieldBinding> implements CompoundButton.OnCheckedChangeListener {
                public CheckboxFieldViewHolder(CommandCheckboxFieldBinding binding) {
                    super(binding);
                    binding.row.setOnClickListener((v) -> {
                        binding.checkbox.toggle();
                    });
                    binding.checkbox.setOnCheckedChangeListener(this);
                }
                protected Element mValue = null;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    binding.label.setText(field.getLabel().or(""));
                    setTextOrHide(binding.desc, field.getDesc());
                    mValue = field.getValue();
                    binding.checkbox.setChecked(mValue.getContent() != null && (mValue.getContent().equals("true") || mValue.getContent().equals("1")));
                }

                @Override
                public void onCheckedChanged(CompoundButton checkbox, boolean isChecked) {
                    if (mValue == null) return;

                    mValue.setContent(isChecked ? "true" : "false");
                }
            }

            class SearchListFieldViewHolder extends ViewHolder<CommandSearchListFieldBinding> implements TextWatcher {
                public SearchListFieldViewHolder(CommandSearchListFieldBinding binding) {
                    super(binding);
                    binding.search.addTextChangedListener(this);
                }
                protected Element mValue = null;
                List<Option> options = new ArrayList<>();
                protected ArrayAdapter<Option> adapter;
                protected boolean open;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    if (field.error != null) {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(field.error);
                        binding.desc.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Design_Error);
                    } else {
                        binding.desc.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Status);
                    }

                    mValue = field.getValue();

                    Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    open = validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null;
                    setupInputType(field.el, binding.search, null);

                    options = field.getOptions();
                    binding.list.setOnItemClickListener((parent, view, position, id) -> {
                        mValue.setContent(adapter.getItem(binding.list.getCheckedItemPosition()).getValue());
                        if (open) binding.search.setText(mValue.getContent());
                    });
                    search("");
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (open) mValue.setContent(s.toString());
                    search(s.toString());
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int count, int after) { }

                protected void search(String s) {
                    List<Option> filteredOptions;
                    final String q = s.replaceAll("\\W", "").toLowerCase();
                    if (q == null || q.equals("")) {
                        filteredOptions = options;
                    } else {
                        filteredOptions = options.stream().filter(o -> o.toString().replaceAll("\\W", "").toLowerCase().contains(q)).collect(Collectors.toList());
                    }
                    adapter = new ArrayAdapter(binding.getRoot().getContext(), R.layout.simple_list_item, filteredOptions);
                    binding.list.setAdapter(adapter);

                    int checkedPos = filteredOptions.indexOf(new Option(mValue.getContent(), ""));
                    if (checkedPos >= 0) binding.list.setItemChecked(checkedPos, true);
                }
            }

            class RadioEditFieldViewHolder extends ViewHolder<CommandRadioEditFieldBinding> implements CompoundButton.OnCheckedChangeListener, TextWatcher {
                public RadioEditFieldViewHolder(CommandRadioEditFieldBinding binding) {
                    super(binding);
                    binding.open.addTextChangedListener(this);
                    options = new ArrayAdapter<Option>(binding.getRoot().getContext(), R.layout.radio_grid_item) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            CompoundButton v = (CompoundButton) super.getView(position, convertView, parent);
                            v.setId(position);
                            v.setChecked(getItem(position).getValue().equals(mValue.getContent()));
                            v.setOnCheckedChangeListener(RadioEditFieldViewHolder.this);
                            return v;
                        }
                    };
                }
                protected Element mValue = null;
                protected ArrayAdapter<Option> options;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    if (field.error != null) {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(field.error);
                        binding.desc.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Design_Error);
                    } else {
                        binding.desc.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Status);
                    }

                    mValue = field.getValue();

                    Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    binding.open.setVisibility((validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null) ? View.VISIBLE : View.GONE);
                    binding.open.setText(mValue.getContent());
                    setupInputType(field.el, binding.open, null);

                    options.clear();
                    List<Option> theOptions = field.getOptions();
                    options.addAll(theOptions);

                    float screenWidth = binding.getRoot().getContext().getResources().getDisplayMetrics().widthPixels;
                    TextPaint paint = ((TextView) LayoutInflater.from(binding.getRoot().getContext()).inflate(R.layout.radio_grid_item, null)).getPaint();
                    float maxColumnWidth = theOptions.stream().map((x) ->
                        StaticLayout.getDesiredWidth(x.toString(), paint)
                    ).max(Float::compare).orElse(new Float(0.0));
                    if (maxColumnWidth * theOptions.size() < 0.90 * screenWidth) {
                        binding.radios.setNumColumns(theOptions.size());
                    } else if (maxColumnWidth * (theOptions.size() / 2) < 0.90 * screenWidth) {
                        binding.radios.setNumColumns(theOptions.size() / 2);
                    } else {
                        binding.radios.setNumColumns(1);
                    }
                    binding.radios.setAdapter(options);
                }

                @Override
                public void onCheckedChanged(CompoundButton radio, boolean isChecked) {
                    if (mValue == null) return;

                    if (isChecked) {
                        mValue.setContent(options.getItem(radio.getId()).getValue());
                        binding.open.setText(mValue.getContent());
                    }
                    options.notifyDataSetChanged();
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (mValue == null) return;

                    mValue.setContent(s.toString());
                    options.notifyDataSetChanged();
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int count, int after) { }
            }

            class SpinnerFieldViewHolder extends ViewHolder<CommandSpinnerFieldBinding> implements AdapterView.OnItemSelectedListener {
                public SpinnerFieldViewHolder(CommandSpinnerFieldBinding binding) {
                    super(binding);
                    binding.spinner.setOnItemSelectedListener(this);
                }
                protected Element mValue = null;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    binding.spinner.setPrompt(field.getLabel().or(""));
                    setTextOrHide(binding.desc, field.getDesc());

                    mValue = field.getValue();

                    ArrayAdapter<Option> options = new ArrayAdapter<Option>(binding.getRoot().getContext(), android.R.layout.simple_spinner_item);
                    options.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    options.addAll(field.getOptions());

                    binding.spinner.setAdapter(options);
                    binding.spinner.setSelection(options.getPosition(new Option(mValue.getContent(), null)));
                }

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    Option o = (Option) parent.getItemAtPosition(pos);
                    if (mValue == null) return;

                    mValue.setContent(o == null ? "" : o.getValue());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    mValue.setContent("");
                }
            }

            class ButtonGridFieldViewHolder extends ViewHolder<CommandButtonGridFieldBinding> {
                public ButtonGridFieldViewHolder(CommandButtonGridFieldBinding binding) {
                    super(binding);
                    options = new ArrayAdapter<Option>(binding.getRoot().getContext(), R.layout.button_grid_item) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            Button v = (Button) super.getView(position, convertView, parent);
                            v.setOnClickListener((view) -> {
                                loading = true;
                                mValue.setContent(getItem(position).getValue());
                                execute();
                            });

                            final SVG icon = getItem(position).getIcon();
                            if (icon != null) {
                                 v.post(() -> {
                                     icon.setDocumentPreserveAspectRatio(com.caverock.androidsvg.PreserveAspectRatio.TOP);
                                     Bitmap bitmap = Bitmap.createBitmap(v.getHeight(), v.getHeight(), Bitmap.Config.ARGB_8888);
                                     Canvas bmcanvas = new Canvas(bitmap);
                                     icon.renderToCanvas(bmcanvas);
                                     v.setCompoundDrawablesRelativeWithIntrinsicBounds(new BitmapDrawable(bitmap), null, null, null);
                                 });
                            }

                            return v;
                        }
                    };
                }
                protected Element mValue = null;
                protected ArrayAdapter<Option> options;
                protected Option defaultOption = null;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    setTextOrHide(binding.label, field.getLabel());
                    setTextOrHide(binding.desc, field.getDesc());

                    if (field.error != null) {
                        binding.desc.setVisibility(View.VISIBLE);
                        binding.desc.setText(field.error);
                        binding.desc.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Design_Error);
                    } else {
                        binding.desc.setTextAppearance(binding.getRoot().getContext(), R.style.TextAppearance_Conversations_Status);
                    }

                    mValue = field.getValue();

                    Element validate = field.el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                    binding.openButton.setVisibility((validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null) ? View.VISIBLE : View.GONE);
                    binding.openButton.setOnClickListener((view) -> {
                        AlertDialog.Builder builder = new AlertDialog.Builder(binding.getRoot().getContext());
                        DialogQuickeditBinding dialogBinding = DataBindingUtil.inflate(LayoutInflater.from(binding.getRoot().getContext()), R.layout.dialog_quickedit, null, false);
                        builder.setPositiveButton(R.string.action_execute, null);
                        if (field.getDesc().isPresent()) {
                            dialogBinding.inputLayout.setHint(field.getDesc().get());
                        }
                        dialogBinding.inputEditText.requestFocus();
                        dialogBinding.inputEditText.getText().append(mValue.getContent());
                        builder.setView(dialogBinding.getRoot());
                        builder.setNegativeButton(R.string.cancel, null);
                        final AlertDialog dialog = builder.create();
                        dialog.setOnShowListener(d -> SoftKeyboardUtils.showKeyboard(dialogBinding.inputEditText));
                        dialog.show();
                        View.OnClickListener clickListener = v -> {
                            loading = true;
                            String value = dialogBinding.inputEditText.getText().toString();
                            mValue.setContent(value);
                            SoftKeyboardUtils.hideSoftKeyboard(dialogBinding.inputEditText);
                            dialog.dismiss();
                            execute();
                        };
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(clickListener);
                        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener((v -> {
                            SoftKeyboardUtils.hideSoftKeyboard(dialogBinding.inputEditText);
                            dialog.dismiss();
                        }));
                        dialog.setCanceledOnTouchOutside(false);
                        dialog.setOnDismissListener(dialog1 -> {
                            SoftKeyboardUtils.hideSoftKeyboard(dialogBinding.inputEditText);
                        });
                    });

                    options.clear();
                    List<Option> theOptions = field.getOptions();

                    defaultOption = null;
                    for (Option option : theOptions) {
                        if (option.getValue().equals(mValue.getContent())) {
                            defaultOption = option;
                            break;
                        }
                    }
                    if (defaultOption == null) {
                        binding.defaultButton.setVisibility(View.GONE);
                    } else {
                        theOptions.remove(defaultOption);
                        binding.defaultButton.setVisibility(View.VISIBLE);

                        final SVG defaultIcon = defaultOption.getIcon();
                        if (defaultIcon != null) {
                             defaultIcon.setDocumentPreserveAspectRatio(com.caverock.androidsvg.PreserveAspectRatio.TOP);
                             DisplayMetrics display = mPager.getContext().getResources().getDisplayMetrics();
                             Bitmap bitmap = Bitmap.createBitmap((int)(display.heightPixels*display.density/4), (int)(display.heightPixels*display.density/4), Bitmap.Config.ARGB_8888);
                             bitmap.setDensity(display.densityDpi);
                             Canvas bmcanvas = new Canvas(bitmap);
                             defaultIcon.renderToCanvas(bmcanvas);
                             binding.defaultButton.setCompoundDrawablesRelativeWithIntrinsicBounds(null, new BitmapDrawable(bitmap), null, null);
                        }

                        binding.defaultButton.setText(defaultOption.toString());
                        binding.defaultButton.setOnClickListener((view) -> {
                            loading = true;
                            mValue.setContent(defaultOption.getValue());
                            execute();
                        });
                    }

                    options.addAll(theOptions);
                    binding.buttons.setAdapter(options);
                }
            }

            class TextFieldViewHolder extends ViewHolder<CommandTextFieldBinding> implements TextWatcher {
                public TextFieldViewHolder(CommandTextFieldBinding binding) {
                    super(binding);
                    binding.textinput.addTextChangedListener(this);
                }
                protected Element mValue = null;

                @Override
                public void bind(Item item) {
                    Field field = (Field) item;
                    binding.textinputLayout.setHint(field.getLabel().or(""));

                    binding.textinputLayout.setHelperTextEnabled(field.getDesc().isPresent());
                    for (String desc : field.getDesc().asSet()) {
                        binding.textinputLayout.setHelperText(desc);
                    }

                    binding.textinputLayout.setErrorEnabled(field.error != null);
                    if (field.error != null) binding.textinputLayout.setError(field.error);

                    mValue = field.getValue();
                    binding.textinput.setText(mValue.getContent());
                    setupInputType(field.el, binding.textinput, binding.textinputLayout);
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
                protected String boundUrl = "";

                @Override
                public void bind(Item oob) {
                    setTextOrHide(binding.desc, Optional.fromNullable(oob.el.findChildContent("desc", "jabber:x:oob")));
                    binding.webview.getSettings().setJavaScriptEnabled(true);
                    binding.webview.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.106 Mobile Safari/537.36");
                    binding.webview.getSettings().setDatabaseEnabled(true);
                    binding.webview.getSettings().setDomStorageEnabled(true);
                    binding.webview.setWebChromeClient(new WebChromeClient() {
                        @Override
                        public void onProgressChanged(WebView view, int newProgress) {
                            binding.progressbar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                            binding.progressbar.setProgress(newProgress);
                        }
                    });
                    binding.webview.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            super.onPageFinished(view, url);
                            mTitle = view.getTitle();
                            ConversationPagerAdapter.this.notifyDataSetChanged();
                        }
                    });
                    final String url = oob.el.findChildContent("url", "jabber:x:oob");
                    if (!boundUrl.equals(url)) {
                        binding.webview.addJavascriptInterface(new JsObject(), "xmpp_xep0050");
                        binding.webview.loadUrl(url);
                        boundUrl = url;
                    }
                }

                class JsObject {
                    @JavascriptInterface
                    public void execute() { execute("execute"); }

                    @JavascriptInterface
                    public void execute(String action) {
                        getView().post(() -> {
                            actionToWebview = null;
                            if(CommandSession.this.execute(action)) {
                                removeSession(CommandSession.this);
                            }
                        });
                    }

                    @JavascriptInterface
                    public void preventDefault() {
                        actionToWebview = binding.webview;
                    }
                }
            }

            class ProgressBarViewHolder extends ViewHolder<CommandProgressBarBinding> {
                public ProgressBarViewHolder(CommandProgressBarBinding binding) { super(binding); }

                @Override
                public void bind(Item item) { }
            }

            class Item {
                protected Element el;
                protected int viewType;
                protected String error = null;

                Item(Element el, int viewType) {
                    this.el = el;
                    this.viewType = viewType;
                }

                public boolean validate() {
                    error = null;
                    return true;
                }
            }

            class Field extends Item {
                Field(eu.siacs.conversations.xmpp.forms.Field el, int viewType) { super(el, viewType); }

                @Override
                public boolean validate() {
                    if (!super.validate()) return false;
                    if (el.findChild("required", "jabber:x:data") == null) return true;
                    if (getValue().getContent() != null && !getValue().getContent().equals("")) return true;

                    error = "this value is required";
                    return false;
                }

                public String getVar() {
                    return el.getAttribute("var");
                }

                public Optional<String> getType() {
                    return Optional.fromNullable(el.getAttribute("type"));
                }

                public Optional<String> getLabel() {
                    String label = el.getAttribute("label");
                    if (label == null) label = getVar();
                    return Optional.fromNullable(label);
                }

                public Optional<String> getDesc() {
                    return Optional.fromNullable(el.findChildContent("desc", "jabber:x:data"));
                }

                public Element getValue() {
                    Element value = el.findChild("value", "jabber:x:data");
                    if (value == null) {
                        value = el.addChild("value", "jabber:x:data");
                    }
                    return value;
                }

                public List<Option> getOptions() {
                    return Option.forField(el);
                }
            }

            class Cell extends Item {
                protected Field reported;

                Cell(Field reported, Element item) {
                    super(item, TYPE_RESULT_CELL);
                    this.reported = reported;
                }
            }

            protected Field mkField(Element el) {
                int viewType = -1;

                String formType = responseElement.getAttribute("type");
                if (formType != null) {
                    String fieldType = el.getAttribute("type");
                    if (fieldType == null) fieldType = "text-single";

                    if (formType.equals("result") || fieldType.equals("fixed")) {
                        viewType = TYPE_RESULT_FIELD;
                    } else if (formType.equals("form")) {
                        if (fieldType.equals("boolean")) {
                            viewType = TYPE_CHECKBOX_FIELD;
                        } else if (fieldType.equals("list-single")) {
                            Element validate = el.findChild("validate", "http://jabber.org/protocol/xdata-validate");
                            if (Option.forField(el).size() > 9) {
                                viewType = TYPE_SEARCH_LIST_FIELD;
                            } else if (fillableFieldCount == 1 && actionsAdapter.countExceptCancel() < 1) {
                                viewType = TYPE_BUTTON_GRID_FIELD;
                            } else if (el.findChild("value", "jabber:x:data") == null || (validate != null && validate.findChild("open", "http://jabber.org/protocol/xdata-validate") != null)) {
                                viewType = TYPE_RADIO_EDIT_FIELD;
                            } else {
                                viewType = TYPE_SPINNER_FIELD;
                            }
                        } else {
                            viewType = TYPE_TEXT_FIELD;
                        }
                    }

                    return new Field(eu.siacs.conversations.xmpp.forms.Field.parse(el), viewType);
                }

                return null;
            }

            protected Item mkItem(Element el, int pos) {
                int viewType = -1;

                if (response != null && response.getType() == IqPacket.TYPE.RESULT) {
                    if (el.getName().equals("note")) {
                        viewType = TYPE_NOTE;
                    } else if (el.getNamespace().equals("jabber:x:oob")) {
                        viewType = TYPE_WEB;
                    } else if (el.getName().equals("instructions") && el.getNamespace().equals("jabber:x:data")) {
                        viewType = TYPE_NOTE;
                    } else if (el.getName().equals("field") && el.getNamespace().equals("jabber:x:data")) {
                        Field field = mkField(el);
                        if (field != null) {
                            items.put(pos, field);
                            return field;
                        }
                    }
                } else if (response != null) {
                    viewType = TYPE_ERROR;
                }

                Item item = new Item(el, viewType);
                items.put(pos, item);
                return item;
            }

            class ActionsAdapter extends ArrayAdapter<Pair<String, String>> {
                protected Context ctx;

                public ActionsAdapter(Context ctx) {
                    super(ctx, R.layout.simple_list_item);
                    this.ctx = ctx;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    TextView tv = (TextView) v.findViewById(android.R.id.text1);
                    tv.setGravity(Gravity.CENTER);
                    tv.setText(getItem(position).second);
                    int resId = ctx.getResources().getIdentifier("action_" + getItem(position).first, "string" , ctx.getPackageName());
                    if (resId != 0 && getItem(position).second.equals(getItem(position).first)) tv.setText(ctx.getResources().getString(resId));
                    tv.setTextColor(ContextCompat.getColor(ctx, R.color.white));
                    tv.setBackgroundColor(UIHelper.getColorForName(getItem(position).first));
                    return v;
                }

                public int getPosition(String s) {
                    for(int i = 0; i < getCount(); i++) {
                        if (getItem(i).first.equals(s)) return i;
                    }
                    return -1;
                }

                public int countExceptCancel() {
                    int count = 0;
                    for(int i = 0; i < getCount(); i++) {
                        if (!getItem(i).first.equals("cancel")) count++;
                    }
                    return count;
                }

                public void clearExceptCancel() {
                    Pair<String,String> cancelItem = null;
                    for(int i = 0; i < getCount(); i++) {
                        if (getItem(i).first.equals("cancel")) cancelItem = getItem(i);
                    }
                    clear();
                    if (cancelItem != null) add(cancelItem);
                }
            }

            final int TYPE_ERROR = 1;
            final int TYPE_NOTE = 2;
            final int TYPE_WEB = 3;
            final int TYPE_RESULT_FIELD = 4;
            final int TYPE_TEXT_FIELD = 5;
            final int TYPE_CHECKBOX_FIELD = 6;
            final int TYPE_SPINNER_FIELD = 7;
            final int TYPE_RADIO_EDIT_FIELD = 8;
            final int TYPE_RESULT_CELL = 9;
            final int TYPE_PROGRESSBAR = 10;
            final int TYPE_SEARCH_LIST_FIELD = 11;
            final int TYPE_ITEM_CARD = 12;
            final int TYPE_BUTTON_GRID_FIELD = 13;

            protected boolean loading = false;
            protected Timer loadingTimer = new Timer();
            protected String mTitle;
            protected String mNode;
            protected CommandPageBinding mBinding = null;
            protected IqPacket response = null;
            protected Element responseElement = null;
            protected List<Field> reported = null;
            protected SparseArray<Item> items = new SparseArray<>();
            protected XmppConnectionService xmppConnectionService;
            protected ActionsAdapter actionsAdapter;
            protected GridLayoutManager layoutManager;
            protected WebView actionToWebview = null;
            protected int fillableFieldCount = 0;

            CommandSession(String title, String node, XmppConnectionService xmppConnectionService) {
                loading();
                mTitle = title;
                mNode = node;
                this.xmppConnectionService = xmppConnectionService;
                if (mPager != null) setupLayoutManager();
                actionsAdapter = new ActionsAdapter(xmppConnectionService);
                actionsAdapter.registerDataSetObserver(new DataSetObserver() {
                    @Override
                    public void onChanged() {
                        if (mBinding == null) return;

                        mBinding.actions.setNumColumns(actionsAdapter.getCount() > 1 ? 2 : 1);
                    }

                    @Override
                    public void onInvalidated() {}
                });
            }

            public String getTitle() {
                return mTitle;
            }

            public void updateWithResponse(IqPacket iq) {
                this.loadingTimer.cancel();
                this.loadingTimer = new Timer();
                this.loading = false;
                this.responseElement = null;
                this.fillableFieldCount = 0;
                this.reported = null;
                this.response = iq;
                this.items.clear();
                this.actionsAdapter.clear();
                layoutManager.setSpanCount(1);

                Element command = iq.findChild("command", "http://jabber.org/protocol/commands");
                if (iq.getType() == IqPacket.TYPE.RESULT && command != null) {
                    if (mNode.equals("jabber:iq:register") && command.getAttribute("status").equals("completed")) {
                        xmppConnectionService.createContact(getAccount().getRoster().getContact(iq.getFrom()), true);
                    }

                    for (Element el : command.getChildren()) {
                        if (el.getName().equals("actions") && el.getNamespace().equals("http://jabber.org/protocol/commands")) {
                            for (Element action : el.getChildren()) {
                                if (!el.getNamespace().equals("http://jabber.org/protocol/commands")) continue;
                                if (action.getName().equals("execute")) continue;

                                actionsAdapter.add(Pair.create(action.getName(), action.getName()));
                            }
                        }
                        if (el.getName().equals("x") && el.getNamespace().equals("jabber:x:data")) {
                            Data form = Data.parse(el);
                            String title = form.getTitle();
                            if (title != null) {
                                mTitle = title;
                                ConversationPagerAdapter.this.notifyDataSetChanged();
                            }

                            if (el.getAttribute("type").equals("result") || el.getAttribute("type").equals("form")) {
                                this.responseElement = el;
                                setupReported(el.findChild("reported", "jabber:x:data"));
                                if (mBinding != null) mBinding.form.setLayoutManager(setupLayoutManager());
                            }

                            eu.siacs.conversations.xmpp.forms.Field actionList = form.getFieldByName("http://jabber.org/protocol/commands#actions");
                            if (actionList != null) {
                                actionsAdapter.clear();

                                for (Option action : actionList.getOptions()) {
                                    actionsAdapter.add(Pair.create(action.getValue(), action.toString()));
                                }
                            }

                            String fillableFieldType = null;
                            for (eu.siacs.conversations.xmpp.forms.Field field : form.getFields()) {
                                if (field.getType() != null && !field.getType().equals("hidden") && !field.getType().equals("fixed") && !field.getFieldName().equals("http://jabber.org/protocol/commands#actions")) {
                                    fillableFieldType = field.getType();
                                    fillableFieldCount++;
                                }
                            }

                            if (fillableFieldCount == 1 && actionsAdapter.countExceptCancel() < 2 && fillableFieldType != null && fillableFieldType.equals("list-single")) {
                                actionsAdapter.clearExceptCancel();
                            }
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

                    if (responseElement == null && command.getAttribute("status") != null && (command.getAttribute("status").equals("completed") || command.getAttribute("status").equals("canceled"))) {
                        removeSession(this);
                        return;
                    }

                    if (command.getAttribute("status").equals("executing") && actionsAdapter.countExceptCancel() < 1 && fillableFieldCount > 1) {
                        // No actions have been given, but we are not done?
                        // This is probably a spec violation, but we should do *something*
                        actionsAdapter.add(Pair.create("execute", "execute"));
                    }

                    if (!actionsAdapter.isEmpty() || fillableFieldCount > 0) {
                        if (command.getAttribute("status").equals("completed") || command.getAttribute("status").equals("canceled")) {
                            actionsAdapter.add(Pair.create("close", "close"));
                        } else if (actionsAdapter.getPosition("cancel") < 0) {
                            actionsAdapter.insert(Pair.create("cancel", "cancel"), 0);
                        }
                    }
                }

                if (actionsAdapter.isEmpty()) {
                    actionsAdapter.add(Pair.create("close", "close"));
                }

                notifyDataSetChanged();
            }

            protected void setupReported(Element el) {
                if (el == null) {
                    reported = null;
                    return;
                }

                reported = new ArrayList<>();
                for (Element fieldEl : el.getChildren()) {
                    if (!fieldEl.getName().equals("field") || !fieldEl.getNamespace().equals("jabber:x:data")) continue;
                    reported.add(mkField(fieldEl));
                }
            }

            @Override
            public int getItemCount() {
                if (loading) return 1;
                if (response == null) return 0;
                if (response.getType() == IqPacket.TYPE.RESULT && responseElement != null && responseElement.getNamespace().equals("jabber:x:data")) {
                    int i = 0;
                    for (Element el : responseElement.getChildren()) {
                        if (!el.getNamespace().equals("jabber:x:data")) continue;
                        if (el.getName().equals("title")) continue;
                        if (el.getName().equals("field")) {
                            String type = el.getAttribute("type");
                            if (type != null && type.equals("hidden")) continue;
                            if (el.getAttribute("var") != null && el.getAttribute("var").equals("http://jabber.org/protocol/commands#actions")) continue;
                        }

                        if (el.getName().equals("reported") || el.getName().equals("item")) {
                            if ((layoutManager == null ? 1 : layoutManager.getSpanCount()) < reported.size()) {
                                if (el.getName().equals("reported")) continue;
                                i += 1;
                            } else {
                                if (reported != null) i += reported.size();
                            }
                            continue;
                        }

                        i++;
                    }
                    return i;
                }
                return 1;
            }

            public Item getItem(int position) {
                if (loading) return new Item(null, TYPE_PROGRESSBAR);
                if (items.get(position) != null) return items.get(position);
                if (response == null) return null;

                if (response.getType() == IqPacket.TYPE.RESULT && responseElement != null) {
                    if (responseElement.getNamespace().equals("jabber:x:data")) {
                        int i = 0;
                        for (Element el : responseElement.getChildren()) {
                            if (!el.getNamespace().equals("jabber:x:data")) continue;
                            if (el.getName().equals("title")) continue;
                            if (el.getName().equals("field")) {
                                String type = el.getAttribute("type");
                                if (type != null && type.equals("hidden")) continue;
                                if (el.getAttribute("var") != null && el.getAttribute("var").equals("http://jabber.org/protocol/commands#actions")) continue;
                            }

                            if (el.getName().equals("reported") || el.getName().equals("item")) {
                                Cell cell = null;

                                if (reported != null) {
                                    if ((layoutManager == null ? 1 : layoutManager.getSpanCount()) < reported.size()) {
                                        if (el.getName().equals("reported")) continue;
                                        if (i == position) {
                                            items.put(position, new Item(el, TYPE_ITEM_CARD));
                                            return items.get(position);
                                        }
                                    } else {
                                        if (reported.size() > position - i) {
                                            Field reportedField = reported.get(position - i);
                                            Element itemField = null;
                                            if (el.getName().equals("item")) {
                                                for (Element subel : el.getChildren()) {
                                                    if (subel.getAttribute("var").equals(reportedField.getVar())) {
                                                       itemField = subel;
                                                       break;
                                                    }
                                                }
                                            }
                                            cell = new Cell(reportedField, itemField);
                                        } else {
                                            i += reported.size();
                                            continue;
                                        }
                                    }
                                }

                                if (cell != null) {
                                    items.put(position, cell);
                                    return cell;
                                }
                            }

                            if (i < position) {
                                i++;
                                continue;
                            }

                            return mkItem(el, position);
                        }
                    }
                }

                return mkItem(responseElement == null ? response : responseElement, position);
            }

            @Override
            public int getItemViewType(int position) {
                return getItem(position).viewType;
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
                    case TYPE_RESULT_CELL: {
                        CommandResultCellBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_result_cell, container, false);
                        return new ResultCellViewHolder(binding);
                    }
                    case TYPE_ITEM_CARD: {
                        CommandItemCardBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_item_card, container, false);
                        return new ItemCardViewHolder(binding);
                    }
                    case TYPE_CHECKBOX_FIELD: {
                        CommandCheckboxFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_checkbox_field, container, false);
                        return new CheckboxFieldViewHolder(binding);
                    }
                    case TYPE_SEARCH_LIST_FIELD: {
                        CommandSearchListFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_search_list_field, container, false);
                        return new SearchListFieldViewHolder(binding);
                    }
                    case TYPE_RADIO_EDIT_FIELD: {
                        CommandRadioEditFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_radio_edit_field, container, false);
                        return new RadioEditFieldViewHolder(binding);
                    }
                    case TYPE_SPINNER_FIELD: {
                        CommandSpinnerFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_spinner_field, container, false);
                        return new SpinnerFieldViewHolder(binding);
                    }
                    case TYPE_BUTTON_GRID_FIELD: {
                        CommandButtonGridFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_button_grid_field, container, false);
                        return new ButtonGridFieldViewHolder(binding);
                    }
                    case TYPE_TEXT_FIELD: {
                        CommandTextFieldBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_text_field, container, false);
                        return new TextFieldViewHolder(binding);
                    }
                    case TYPE_PROGRESSBAR: {
                        CommandProgressBarBinding binding = DataBindingUtil.inflate(LayoutInflater.from(container.getContext()), R.layout.command_progress_bar, container, false);
                        return new ProgressBarViewHolder(binding);
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

            public boolean validate() {
                int count = getItemCount();
                boolean isValid = true;
                for (int i = 0; i < count; i++) {
                    boolean oneIsValid = getItem(i).validate();
                    isValid = isValid && oneIsValid;
                }
                notifyDataSetChanged();
                return isValid;
            }

            public boolean execute() {
                return execute("execute");
            }

            public boolean execute(int actionPosition) {
                return execute(actionsAdapter.getItem(actionPosition).first);
            }

            public boolean execute(String action) {
                if (!action.equals("cancel") && !action.equals("prev") && !validate()) return false;

                if (response == null) return true;
                Element command = response.findChild("command", "http://jabber.org/protocol/commands");
                if (command == null) return true;
                String status = command.getAttribute("status");
                if (status == null || (!status.equals("executing") && !action.equals("prev"))) return true;

                if (actionToWebview != null) {
                    actionToWebview.postWebMessage(new WebMessage("xmpp_xep0050/" + action), Uri.parse("*"));
                    return false;
                }

                final IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
                packet.setTo(response.getFrom());
                final Element c = packet.addChild("command", Namespace.COMMANDS);
                c.setAttribute("node", mNode);
                c.setAttribute("sessionid", command.getAttribute("sessionid"));

                String formType = responseElement == null ? null : responseElement.getAttribute("type");
                if (!action.equals("cancel") &&
                    !action.equals("prev") &&
                    responseElement != null &&
                    responseElement.getName().equals("x") &&
                    responseElement.getNamespace().equals("jabber:x:data") &&
                    formType != null && formType.equals("form")) {

                    Data form = Data.parse(responseElement);
                    eu.siacs.conversations.xmpp.forms.Field actionList = form.getFieldByName("http://jabber.org/protocol/commands#actions");
                    if (actionList != null) {
                        actionList.setValue(action);
                        c.setAttribute("action", "execute");
                    }

                    responseElement.setAttribute("type", "submit");
                    Element rsm = responseElement.findChild("set", "http://jabber.org/protocol/rsm");
                    if (rsm != null) {
                        Element max = new Element("max", "http://jabber.org/protocol/rsm");
                        max.setContent("1000");
                        rsm.addChild(max);
                    }

                    c.addChild(responseElement);
                }

                if (c.getAttribute("action") == null) c.setAttribute("action", action);

                xmppConnectionService.sendIqPacket(getAccount(), packet, (a, iq) -> {
                    getView().post(() -> {
                        updateWithResponse(iq);
                    });
                });

                loading();
                return false;
            }

            protected void loading() {
                loadingTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        getView().post(() -> {
                            loading = true;
                            notifyDataSetChanged();
                        });
                    }
                }, 500);
            }

            protected GridLayoutManager setupLayoutManager() {
                int spanCount = 1;

                if (reported != null && mPager != null) {
                    float screenWidth = mPager.getContext().getResources().getDisplayMetrics().widthPixels;
                    TextPaint paint = ((TextView) LayoutInflater.from(mPager.getContext()).inflate(R.layout.command_result_cell, null)).getPaint();
                    float tableHeaderWidth = reported.stream().reduce(
                        0f,
                        (total, field) -> total + StaticLayout.getDesiredWidth(field.getLabel().or("--------"), paint),
                        (a, b) -> a + b
                    );

                    spanCount = tableHeaderWidth > 0.65 * screenWidth ? 1 : this.reported.size();
                }

                if (layoutManager != null && layoutManager.getSpanCount() != spanCount) {
                    items.clear();
                    notifyDataSetChanged();
                }

                layoutManager = new GridLayoutManager(mPager.getContext(), spanCount);
                layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        if (getItemViewType(position) != TYPE_RESULT_CELL) return layoutManager.getSpanCount();
                        return 1;
                    }
                });
                return layoutManager;
            }

            public void setBinding(CommandPageBinding b) {
                mBinding = b;
                // https://stackoverflow.com/a/32350474/8611
                mBinding.form.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                    @Override
                    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                        if(rv.getChildCount() > 0) {
                            int[] location = new int[2];
                            rv.getLocationOnScreen(location);
                            View childView = rv.findChildViewUnder(e.getX(), e.getY());
                            if (childView instanceof ViewGroup) {
                                childView = findViewAt((ViewGroup) childView, location[0] + e.getX(), location[1] + e.getY());
                            }
                            if ((childView instanceof ListView && ((ListView) childView).canScrollList(1)) || childView instanceof WebView) {
                                int action = e.getAction();
                                switch (action) {
                                    case MotionEvent.ACTION_DOWN:
                                        rv.requestDisallowInterceptTouchEvent(true);
                                }
                            }
                        }

                        return false;
                    }

                    @Override
                    public void onRequestDisallowInterceptTouchEvent(boolean disallow) { }

                    @Override
                    public void onTouchEvent(RecyclerView rv, MotionEvent e) { }
                });
                mBinding.form.setLayoutManager(setupLayoutManager());
                mBinding.form.setAdapter(this);
                mBinding.actions.setAdapter(actionsAdapter);
                mBinding.actions.setOnItemClickListener((parent, v, pos, id) -> {
                    if (execute(pos)) {
                        removeSession(CommandSession.this);
                    }
                });

                actionsAdapter.notifyDataSetChanged();
            }

            // https://stackoverflow.com/a/36037991/8611
            private View findViewAt(ViewGroup viewGroup, float x, float y) {
                for(int i = 0; i < viewGroup.getChildCount(); i++) {
                    View child = viewGroup.getChildAt(i);
                    if (child instanceof ViewGroup && !(child instanceof ListView) && !(child instanceof WebView)) {
                        View foundView = findViewAt((ViewGroup) child, x, y);
                        if (foundView != null && foundView.isShown()) {
                            return foundView;
                        }
                    } else {
                        int[] location = new int[2];
                        child.getLocationOnScreen(location);
                        Rect rect = new Rect(location[0], location[1], location[0] + child.getWidth(), location[1] + child.getHeight());
                        if (rect.contains((int)x, (int)y)) {
                            return child;
                        }
                    }
                }

                return null;
            }
        }
    }
}
