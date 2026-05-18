package com.cheogram.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.google.common.io.Files;

import java.io.File;
import java.util.ArrayList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentExtensionSettingsBinding;
import eu.siacs.conversations.databinding.ExtensionItemBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.StubConversation;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.worker.ExportBackupWorker;

public class ExtensionSettingsFragment extends androidx.fragment.app.Fragment {
	FragmentExtensionSettingsBinding binding;
	ExtensionAdapter extensionAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = DataBindingUtil.inflate(inflater, R.layout.fragment_extension_settings, container, false);
		binding.addExtension.setOnClickListener((v) -> {
			final var intent = new Intent();
			intent.setAction(Intent.ACTION_GET_CONTENT);
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			intent.setType("*/*");
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.perform_action_with)), 0x1);
		});

		extensionAdapter = new ExtensionAdapter(inflater);
		binding.extensionList.setAdapter(extensionAdapter);
		extensionAdapter.refresh();

		return binding.getRoot();
	}

	@Override
	public void onSaveInstanceState(Bundle bundle) {
		super.onSaveInstanceState(bundle);
	}

	@Override
	public void onStart() {
		super.onStart();
		getActivity().setTitle("Extensions");
		if (extensionAdapter != null) {
			extensionAdapter.refresh();
			extensionAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		extensionAdapter = null;
		binding = null;
	}

	public void addExtension(Uri uri) {
		final var xmppConnectionService = ((XmppActivity) requireActivity()).xmppConnectionService;
		if (xmppConnectionService == null) return;
		try {
			final var fileBackend = xmppConnectionService.getFileBackend();
			final var base = fileBackend.calculateCids(fileBackend.openInputStream(uri))[0].toString();
			final var target = new File(new File(xmppConnectionService.getExternalFilesDir(null), "extensions"), base + ".xdc");
			fileBackend.copyFileToPrivateStorage(target, uri);
		} catch (final Exception e) {
			Toast.makeText(requireActivity(), "Could not copy extension: " + e, Toast.LENGTH_SHORT).show();
		}
	}


	@Override
	public void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		for (final var attachment : Attachment.extractAttachments(requireActivity(), data, Attachment.Type.FILE)) {
			if ("application/webxdc+zip".equals(attachment.getMime())) addExtension(attachment.getUri());
		}
		if (extensionAdapter != null) {
			extensionAdapter.refresh();
			extensionAdapter.notifyDataSetChanged();
		}
	}

	protected class ExtensionAdapter extends RecyclerView.Adapter<WebxdcViewHolder> {
		final LayoutInflater inflater;
		final ArrayList<ExtensionPreview> xdcs = new ArrayList<>();

		ExtensionAdapter(final LayoutInflater inflater) {
			this.inflater = inflater;
		}

		public void refresh() {
			xdcs.clear();
			final var activity = (XmppActivity) requireActivity();
			final var xmppConnectionService = activity.xmppConnectionService;
			if (xmppConnectionService == null) return;
			final var dir = new File(xmppConnectionService.getExternalFilesDir(null), "extensions");
			for (File file : Files.fileTraverser().breadthFirst(dir)) {
				if (file.isFile() && file.canRead()) {
					final var xdc = new WebxdcPage(activity, file, createExtensionPreviewSource(file));
					try {
						xdcs.add(new ExtensionPreview(file, xdc.getName()));
					} finally {
						xdc.close();
					}
				}
			}
		}

		@Override
		public int getItemCount() {
			return xdcs.size();
		}

		@Override
		public WebxdcViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			final ExtensionItemBinding binding = DataBindingUtil.inflate(inflater, R.layout.extension_item, parent, false);
			return new WebxdcViewHolder(binding);
		}

		@Override
		public void onBindViewHolder(WebxdcViewHolder holder, int position) {
			holder.bind((XmppActivity) requireActivity(), xdcs.get(position));
		}
	}

	private static Message createExtensionPreviewSource(final File file) {
		final var source = new Message(new StubConversation(null, "", null, 0), null, Message.ENCRYPTION_NONE);
		source.setStatus(Message.STATUS_DUMMY);
		source.setUuid(file.getName());
		return source;
	}

	protected static class ExtensionPreview {
		final File file;
		final String name;
		android.graphics.drawable.Drawable icon;

		ExtensionPreview(final File file, final String name) {
			this.file = file;
			this.name = name;
		}
	}

	protected static class WebxdcViewHolder extends RecyclerView.ViewHolder {
		final ExtensionItemBinding binding;

		public WebxdcViewHolder(final ExtensionItemBinding binding) {
			super(binding.getRoot());
			this.binding = binding;
		}

		public void bind(final XmppActivity activity, ExtensionPreview xdc) {
			if (xdc.icon == null) {
				final var page = new WebxdcPage(activity, xdc.file, createExtensionPreviewSource(xdc.file));
				try {
					xdc.icon = page.getIcon();
				} finally {
					page.close();
				}
			}
			binding.name.setText(xdc.name);
			binding.icon.setImageDrawable(xdc.icon);
		}
	}
}
