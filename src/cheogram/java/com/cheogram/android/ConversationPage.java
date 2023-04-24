package com.cheogram.android;

import android.content.Context;
import android.view.View;

import eu.siacs.conversations.utils.Consumer;

public interface ConversationPage {
	public String getTitle();
	public String getNode();
	public View inflateUi(Context context, Consumer<ConversationPage> remover);
	public View getView();
	public void refresh();
}
