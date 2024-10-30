package com.cheogram.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.widget.EditMessage;

public class EditMessageSelectionActionModeCallback implements ActionMode.Callback {

	private final EditMessage editMessage;

	public EditMessageSelectionActionModeCallback(EditMessage editMessage) {
		this.editMessage = editMessage;
	}

	@Override
	public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
		final MenuInflater inflater = mode.getMenuInflater();
		inflater.inflate(R.menu.edit_message_selection_actions, menu);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		menu.findItem(R.id.bold).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.findItem(R.id.italic).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	@Override
	public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
		if (item.getItemId() == R.id.bold) {
			final var start = editMessage.getSelectionStart();
			final var end = editMessage.getSelectionEnd();
			if (start < 0 || end < 0) return false;
			editMessage.getText().insert(start, "*");
			editMessage.getText().insert(end+1, "*");
			return true;
		}
		if (item.getItemId() == R.id.italic) {
			final var start = editMessage.getSelectionStart();
			final var end = editMessage.getSelectionEnd();
			if (start < 0 || end < 0) return false;
			editMessage.getText().insert(start, "_");
			editMessage.getText().insert(end+1, "_");
			return true;
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {}
}
