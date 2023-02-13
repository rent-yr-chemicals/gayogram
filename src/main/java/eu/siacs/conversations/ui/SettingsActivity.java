package eu.siacs.conversations.ui;

import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.File;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.ExportBackupService;
import eu.siacs.conversations.services.MemorizingTrustManager;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.UnifiedPushDistributor;
import eu.siacs.conversations.ui.util.SettingsUtils;
import eu.siacs.conversations.ui.util.StyledAttributes;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.xmpp.Jid;

public class SettingsActivity extends XmppActivity implements OnSharedPreferenceChangeListener {

    public static final String KEEP_FOREGROUND_SERVICE = "enable_foreground_service";
    public static final String AWAY_WHEN_SCREEN_IS_OFF = "away_when_screen_off";
    public static final String TREAT_VIBRATE_AS_SILENT = "treat_vibrate_as_silent";
    public static final String DND_ON_SILENT_MODE = "dnd_on_silent_mode";
    public static final String MANUALLY_CHANGE_PRESENCE = "manually_change_presence";
    public static final String BLIND_TRUST_BEFORE_VERIFICATION = "btbv";
    public static final String AUTOMATIC_MESSAGE_DELETION = "automatic_message_deletion";
    public static final String BROADCAST_LAST_ACTIVITY = "last_activity";
    public static final String THEME = "theme";
    public static final String SHOW_DYNAMIC_TAGS = "show_dynamic_tags";
    public static final String OMEMO_SETTING = "omemo";
    public static final String PREVENT_SCREENSHOTS = "prevent_screenshots";

    public static final int REQUEST_CREATE_BACKUP = 0xbf8701;

    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        FragmentManager fm = getFragmentManager();
        mSettingsFragment = (SettingsFragment) fm.findFragmentById(R.id.settings_content);
        if (mSettingsFragment == null
                || !mSettingsFragment.getClass().equals(SettingsFragment.class)) {
            mSettingsFragment = new SettingsFragment();
            fm.beginTransaction().replace(R.id.settings_content, mSettingsFragment).commit();
        }
        mSettingsFragment.setActivityIntent(getIntent());
        this.mTheme = findTheme();
        setTheme(this.mTheme);
        ThemeHelper.applyCustomColors(this);
        getWindow()
                .getDecorView()
                .setBackgroundColor(
                        StyledAttributes.getColor(this, R.attr.color_background_primary));
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data.getData() == null) return;

        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        p.edit().putString("sticker_directory", data.getData().toString()).commit();
    }

    @Override
    void onBackendConnected() {
        boolean diallerIntegrationPossible = false;

        if (Build.VERSION.SDK_INT >= 23) {
            outer:
            for (Account account : xmppConnectionService.getAccounts()) {
                for (Contact contact : account.getRoster().getContacts()) {
                    if (contact.getPresences().anyIdentity("gateway", "pstn")) {
                        diallerIntegrationPossible = true;
                        break outer;
                    }
                }
            }
        }
        if (!diallerIntegrationPossible) {
            PreferenceCategory cat = (PreferenceCategory) mSettingsFragment.findPreference("notification_category");
            Preference pref = mSettingsFragment.findPreference("dialler_integration_incoming");
            if (cat != null && pref != null) cat.removePreference(pref);
        }
        final Preference accountPreference =
                mSettingsFragment.findPreference(UnifiedPushDistributor.PREFERENCE_ACCOUNT);
        reconfigureUpAccountPreference(accountPreference);
    }

    private void reconfigureUpAccountPreference(final Preference preference) {
        final ListPreference listPreference;
        if (preference instanceof ListPreference) {
            listPreference = (ListPreference) preference;
        } else {
            return;
        }
        final List<CharSequence> accounts =
                ImmutableList.copyOf(
                        Lists.transform(
                                xmppConnectionService.getAccounts(),
                                a -> a.getJid().asBareJid().toEscapedString()));
        final ImmutableList.Builder<CharSequence> entries = new ImmutableList.Builder<>();
        final ImmutableList.Builder<CharSequence> entryValues = new ImmutableList.Builder<>();
        entries.add(getString(R.string.no_account_deactivated));
        entryValues.add("none");
        entries.addAll(accounts);
        entryValues.addAll(accounts);
        listPreference.setEntries(entries.build().toArray(new CharSequence[0]));
        listPreference.setEntryValues(entryValues.build().toArray(new CharSequence[0]));
        if (!accounts.contains(listPreference.getValue())) {
            listPreference.setValue("none");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

        changeOmemoSettingSummary();

        if (QuickConversationsService.isQuicksy()
                || Strings.isNullOrEmpty(Config.CHANNEL_DISCOVERY)) {
            final PreferenceCategory groupChats =
                    (PreferenceCategory) mSettingsFragment.findPreference("group_chats");
            final Preference channelDiscoveryMethod =
                    mSettingsFragment.findPreference("channel_discovery_method");
            if (groupChats != null && channelDiscoveryMethod != null) {
                groupChats.removePreference(channelDiscoveryMethod);
            }
        }

        if (QuickConversationsService.isQuicksy()) {
            final PreferenceCategory connectionOptions =
                    (PreferenceCategory) mSettingsFragment.findPreference("connection_options");
            PreferenceScreen expert = (PreferenceScreen) mSettingsFragment.findPreference("expert");
            if (connectionOptions != null) {
                expert.removePreference(connectionOptions);
            }
        }

        PreferenceScreen mainPreferenceScreen =
                (PreferenceScreen) mSettingsFragment.findPreference("main_screen");

        PreferenceCategory attachmentsCategory =
                (PreferenceCategory) mSettingsFragment.findPreference("attachments");
        CheckBoxPreference locationPlugin =
                (CheckBoxPreference) mSettingsFragment.findPreference("use_share_location_plugin");
        if (attachmentsCategory != null && locationPlugin != null) {
            if (!GeoHelper.isLocationPluginInstalled(this)) {
                attachmentsCategory.removePreference(locationPlugin);
            }
        }

        // this feature is only available on Huawei Android 6.
        PreferenceScreen huaweiPreferenceScreen =
                (PreferenceScreen) mSettingsFragment.findPreference("huawei");
        if (huaweiPreferenceScreen != null) {
            Intent intent = huaweiPreferenceScreen.getIntent();
            // remove when Api version is above M (Version 6.0) or if the intent is not callable
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M || !isCallable(intent)) {
                PreferenceCategory generalCategory =
                        (PreferenceCategory) mSettingsFragment.findPreference("general");
                generalCategory.removePreference(huaweiPreferenceScreen);
                if (generalCategory.getPreferenceCount() == 0) {
                    if (mainPreferenceScreen != null) {
                        mainPreferenceScreen.removePreference(generalCategory);
                    }
                }
            }
        }

        ListPreference automaticMessageDeletionList =
                (ListPreference) mSettingsFragment.findPreference(AUTOMATIC_MESSAGE_DELETION);
        if (automaticMessageDeletionList != null) {
            final int[] choices =
                    getResources().getIntArray(R.array.automatic_message_deletion_values);
            CharSequence[] entries = new CharSequence[choices.length];
            CharSequence[] entryValues = new CharSequence[choices.length];
            for (int i = 0; i < choices.length; ++i) {
                entryValues[i] = String.valueOf(choices[i]);
                if (choices[i] == 0) {
                    entries[i] = getString(R.string.never);
                } else {
                    entries[i] = TimeFrameUtils.resolve(this, 1000L * choices[i]);
                }
            }
            automaticMessageDeletionList.setEntries(entries);
            automaticMessageDeletionList.setEntryValues(entryValues);
        }

        boolean removeLocation =
                new Intent("eu.siacs.conversations.location.request")
                                .resolveActivity(getPackageManager())
                        == null;
        boolean removeVoice =
                new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
                                .resolveActivity(getPackageManager())
                        == null;

        ListPreference quickAction =
                (ListPreference) mSettingsFragment.findPreference("quick_action");
        if (quickAction != null && (removeLocation || removeVoice)) {
            ArrayList<CharSequence> entries =
                    new ArrayList<>(Arrays.asList(quickAction.getEntries()));
            ArrayList<CharSequence> entryValues =
                    new ArrayList<>(Arrays.asList(quickAction.getEntryValues()));
            int index = entryValues.indexOf("location");
            if (index > 0 && removeLocation) {
                entries.remove(index);
                entryValues.remove(index);
            }
            index = entryValues.indexOf("voice");
            if (index > 0 && removeVoice) {
                entries.remove(index);
                entryValues.remove(index);
            }
            quickAction.setEntries(entries.toArray(new CharSequence[entries.size()]));
            quickAction.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
        }

        final Preference removeCertsPreference =
                mSettingsFragment.findPreference("remove_trusted_certificates");
        if (removeCertsPreference != null) {
            removeCertsPreference.setOnPreferenceClickListener(
                    preference -> {
                        final MemorizingTrustManager mtm =
                                xmppConnectionService.getMemorizingTrustManager();
                        final ArrayList<String> aliases = Collections.list(mtm.getCertificates());
                        if (aliases.size() == 0) {
                            displayToast(getString(R.string.toast_no_trusted_certs));
                            return true;
                        }
                        final ArrayList<Integer> selectedItems = new ArrayList<>();
                        final AlertDialog.Builder dialogBuilder =
                                new AlertDialog.Builder(SettingsActivity.this);
                        dialogBuilder.setTitle(
                                getResources().getString(R.string.dialog_manage_certs_title));
                        dialogBuilder.setMultiChoiceItems(
                                aliases.toArray(new CharSequence[aliases.size()]),
                                null,
                                (dialog, indexSelected, isChecked) -> {
                                    if (isChecked) {
                                        selectedItems.add(indexSelected);
                                    } else if (selectedItems.contains(indexSelected)) {
                                        selectedItems.remove(Integer.valueOf(indexSelected));
                                    }
                                    ((AlertDialog) dialog)
                                            .getButton(DialogInterface.BUTTON_POSITIVE)
                                            .setEnabled(selectedItems.size() > 0);
                                });

                        dialogBuilder.setPositiveButton(
                                getResources()
                                        .getString(R.string.dialog_manage_certs_positivebutton),
                                (dialog, which) -> {
                                    int count = selectedItems.size();
                                    if (count > 0) {
                                        for (int i = 0; i < count; i++) {
                                            try {
                                                Integer item =
                                                        Integer.valueOf(
                                                                selectedItems.get(i).toString());
                                                String alias = aliases.get(item);
                                                mtm.deleteCertificate(alias);
                                            } catch (KeyStoreException e) {
                                                e.printStackTrace();
                                                displayToast("Error: " + e.getLocalizedMessage());
                                            }
                                        }
                                        if (xmppConnectionServiceBound) {
                                            reconnectAccounts();
                                        }
                                        displayToast(
                                                getResources()
                                                        .getQuantityString(
                                                                R.plurals.toast_delete_certificates,
                                                                count,
                                                                count));
                                    }
                                });
                        dialogBuilder.setNegativeButton(
                                getResources()
                                        .getString(R.string.dialog_manage_certs_negativebutton),
                                null);
                        AlertDialog removeCertsDialog = dialogBuilder.create();
                        removeCertsDialog.show();
                        removeCertsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        return true;
                    });
        }

        final Preference createBackupPreference = mSettingsFragment.findPreference("create_backup");
        if (createBackupPreference != null) {
            createBackupPreference.setSummary(
                    getString(
                            R.string.pref_create_backup_summary,
                            FileBackend.getBackupDirectory(this).getAbsolutePath()));
            createBackupPreference.setOnPreferenceClickListener(
                    preference -> {
                        if (hasStoragePermission(REQUEST_CREATE_BACKUP)) {
                            createBackup();
                        }
                        return true;
                    });
        }

        if (Config.ONLY_INTERNAL_STORAGE) {
            final Preference cleanCachePreference = mSettingsFragment.findPreference("clean_cache");
            if (cleanCachePreference != null) {
                cleanCachePreference.setOnPreferenceClickListener(preference -> cleanCache());
            }

            final Preference cleanPrivateStoragePreference =
                    mSettingsFragment.findPreference("clean_private_storage");
            if (cleanPrivateStoragePreference != null) {
                cleanPrivateStoragePreference.setOnPreferenceClickListener(
                        preference -> cleanPrivateStorage());
            }
        }

        final Preference deleteOmemoPreference =
                mSettingsFragment.findPreference("delete_omemo_identities");
        if (deleteOmemoPreference != null) {
            deleteOmemoPreference.setOnPreferenceClickListener(
                    preference -> deleteOmemoIdentities());
        }
        if (Config.omemoOnly()) {
            final PreferenceCategory privacyCategory =
                    (PreferenceCategory) mSettingsFragment.findPreference("privacy");
            final Preference omemoPreference =mSettingsFragment.findPreference(OMEMO_SETTING);
            if (omemoPreference != null) {
                privacyCategory.removePreference(omemoPreference);
            }
        }

        final Preference stickerDir = mSettingsFragment.findPreference("sticker_directory");
        if (stickerDir != null) {
            stickerDir.setOnPreferenceClickListener((p) -> {
                Intent intent = ((StorageManager) getSystemService(Context.STORAGE_SERVICE)).getPrimaryStorageVolume().createOpenDocumentTreeIntent();
                startActivityForResult(Intent.createChooser(intent, "Choose sticker location"), 0);
                return true;
            });
        }

        final String theTheme = PreferenceManager.getDefaultSharedPreferences(this).getString(THEME, "");
        if (Build.VERSION.SDK_INT < 30 || !theTheme.equals("custom")) {
            final PreferenceCategory uiCategory = (PreferenceCategory) mSettingsFragment.findPreference("ui");
            final Preference customTheme = mSettingsFragment.findPreference("custom_theme");
            if (customTheme != null) uiCategory.removePreference(customTheme);
        }
    }

    private void changeOmemoSettingSummary() {
        final ListPreference omemoPreference =
                (ListPreference) mSettingsFragment.findPreference(OMEMO_SETTING);
        if (omemoPreference == null) {
            return;
        }
        final String value = omemoPreference.getValue();
        switch (value) {
            case "always":
                omemoPreference.setSummary(R.string.pref_omemo_setting_summary_always);
                break;
            case "default_on":
                omemoPreference.setSummary(R.string.pref_omemo_setting_summary_default_on);
                break;
            case "default_off":
                omemoPreference.setSummary(R.string.pref_omemo_setting_summary_default_off);
                break;
        }
    }

    private boolean isCallable(final Intent i) {
        return i != null
                && getPackageManager()
                                .queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
                                .size()
                        > 0;
    }

    private boolean cleanCache() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        return true;
    }

    private boolean cleanPrivateStorage() {
        for (String type : Arrays.asList("Images", "Videos", "Files", "Recordings")) {
            cleanPrivateFiles(type);
        }
        return true;
    }

    private void cleanPrivateFiles(final String type) {
        try {
            File dir = new File(getFilesDir().getAbsolutePath(), "/" + type + "/");
            File[] array = dir.listFiles();
            if (array != null) {
                for (int b = 0; b < array.length; b++) {
                    String name = array[b].getName().toLowerCase();
                    if (name.equals(".nomedia")) {
                        continue;
                    }
                    if (array[b].isFile()) {
                        array[b].delete();
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("CleanCache", e.toString());
        }
    }

    private boolean deleteOmemoIdentities() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_delete_omemo_identities);
        final List<CharSequence> accounts = new ArrayList<>();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                accounts.add(account.getJid().asBareJid().toString());
            }
        }
        final boolean[] checkedItems = new boolean[accounts.size()];
        builder.setMultiChoiceItems(
                accounts.toArray(new CharSequence[accounts.size()]),
                checkedItems,
                (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                    final AlertDialog alertDialog = (AlertDialog) dialog;
                    for (boolean item : checkedItems) {
                        if (item) {
                            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                            return;
                        }
                    }
                    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(
                R.string.delete_selected_keys,
                (dialog, which) -> {
                    for (int i = 0; i < checkedItems.length; ++i) {
                        if (checkedItems[i]) {
                            try {
                                Jid jid = Jid.of(accounts.get(i).toString());
                                Account account = xmppConnectionService.findAccountByJid(jid);
                                if (account != null) {
                                    account.getAxolotlService().regenerateKeys(true);
                                }
                            } catch (IllegalArgumentException e) {
                                //
                            }
                        }
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
        final List<String> resendPresence =
                Arrays.asList(
                        "confirm_messages",
                        DND_ON_SILENT_MODE,
                        AWAY_WHEN_SCREEN_IS_OFF,
                        "allow_message_correction",
                        TREAT_VIBRATE_AS_SILENT,
                        MANUALLY_CHANGE_PRESENCE,
                        BROADCAST_LAST_ACTIVITY);
        if (name.equals(OMEMO_SETTING)) {
            OmemoSetting.load(this, preferences);
            changeOmemoSettingSummary();
        } else if (name.equals(KEEP_FOREGROUND_SERVICE)) {
            xmppConnectionService.toggleForegroundService();
        } else if (resendPresence.contains(name)) {
            if (xmppConnectionServiceBound) {
                if (name.equals(AWAY_WHEN_SCREEN_IS_OFF) || name.equals(MANUALLY_CHANGE_PRESENCE)) {
                    xmppConnectionService.toggleScreenEventReceiver();
                }
                xmppConnectionService.refreshAllPresences();
            }
        } else if (name.equals("dont_trust_system_cas")) {
            xmppConnectionService.updateMemorizingTrustmanager();
            reconnectAccounts();
        } else if (name.equals("use_tor")) {
            if (preferences.getBoolean(name, false)) {
                displayToast(getString(R.string.audio_video_disabled_tor));
            }
            reconnectAccounts();
            xmppConnectionService.reinitializeMuclumbusService();
        } else if (name.equals(AUTOMATIC_MESSAGE_DELETION)) {
            xmppConnectionService.expireOldMessages(true);
        } else if (name.equals(THEME) || name.equals("custom_theme_primary") || name.equals("custom_theme_primary_dark") || name.equals("custom_theme_accent") || name.equals("custom_theme_dark")) {
            final int theme = findTheme();
            xmppConnectionService.setTheme(theme);
            ThemeHelper.applyCustomColors(xmppConnectionService);
            recreate();
        } else if (name.equals(PREVENT_SCREENSHOTS)) {
            SettingsUtils.applyScreenshotPreventionSetting(this);
        } else if (UnifiedPushDistributor.PREFERENCES.contains(name)) {
            if (xmppConnectionService.reconfigurePushDistributor()) {
                xmppConnectionService.renewUnifiedPushEndpoints();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SettingsUtils.applyScreenshotPreventionSetting(this);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_CREATE_BACKUP) {
                    createBackup();
                }
            } else {
                Toast.makeText(
                                this,
                                getString(
                                        R.string.no_storage_permission,
                                        getString(R.string.app_name)),
                                Toast.LENGTH_SHORT)
                        .show();
            }
    }

    private void createBackup() {
        new AlertDialog.Builder(this)
            .setTitle("Create Backup")
            .setMessage("Export extra Cheogram-only data (backup will not import into other apps then)?")
            .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                createBackup(true);
            })
            .setNegativeButton(R.string.no, (dialog, whichButton) -> {
                createBackup(false);
            }).show();
    }

    private void createBackup(boolean withCheogramDb) {
        Intent intent = new Intent(this, ExportBackupService.class);
        intent.putExtra("cheogram_db", withCheogramDb);
        ContextCompat.startForegroundService(this, intent);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.backup_started_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    private void reconnectAccounts() {
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                xmppConnectionService.reconnectAccountInBackground(account);
            }
        }
    }

    public void refreshUiReal() {
        // nothing to do. This Activity doesn't implement any listeners
    }
}
