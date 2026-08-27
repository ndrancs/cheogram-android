package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.ImageView;
import androidx.annotation.DimenRes;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.ui.XmppActivity;
import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;

public class AvatarWorkerTask extends AsyncTask<AvatarService.Avatar, Void, Drawable> {
    private final WeakReference<ImageView> imageViewReference;
    private final WeakReference<XmppActivity> activityReference;
    private AvatarService.Avatar avatarable = null;
    private @DimenRes final int size;

    public AvatarWorkerTask(ImageView imageView, @DimenRes int size) {
        imageViewReference = new WeakReference<>(imageView);
        activityReference = null;
        this.size = size;
    }


    public AvatarWorkerTask(XmppActivity activity, @DimenRes int size) {
        activityReference = new WeakReference<>(activity);
        imageViewReference = new WeakReference<>(null);
        this.size = size;
    }

    @Override
    protected Drawable doInBackground(AvatarService.Avatar... params) {
        this.avatarable = params[0];
        final XmppActivity activity = activityReference == null ? XmppActivity.find(imageViewReference) : activityReference.get();
        if (activity == null) {
            return null;
        }
        return activity.avatarService()
                .get(avatarable, (int) activity.getResources().getDimension(size), isCancelled());
    }

    @Override
    protected void onPostExecute(Drawable bitmap) {
        if (bitmap != null && !isCancelled()) {
            final ImageView imageView = imageViewReference.get();
            if (imageView != null) {
                imageView.setImageDrawable(bitmap);
                imageView.setBackgroundColor(0x00000000);
            }
            if (Build.VERSION.SDK_INT >= 28 && bitmap instanceof AnimatedImageDrawable) {
                ((AnimatedImageDrawable) bitmap).start();
            }
            if (activityReference != null) {
                final var activity = activityReference.get();
                if (activity != null) {
                    activity.refreshUi();
                }
            }
        }
    }

    public static boolean cancelPotentialWork(AvatarService.Avatar avatar, ImageView imageView) {
        final AvatarWorkerTask workerTask = getBitmapWorkerTask(imageView);

        if (workerTask != null) {
            final AvatarService.Avatar old = workerTask.avatarable;
            if (old == null || avatar != old) {
                workerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    public static AvatarWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable asyncDrawable) {
                return asyncDrawable.getAvatarWorkerTask();
            }
        }
        return null;
    }

    public static void loadAvatar(
            final AvatarService.Avatar avatar,
            final ImageView imageView,
            final @DimenRes int size) {
        if (cancelPotentialWork(avatar, imageView)) {
            final XmppActivity activity = XmppActivity.find(imageView);
            if (activity == null) {
                return;
            }
            final Drawable bm = activity.avatarService().get(avatar, (int) activity.getResources().getDimension(size), true);
            setContentDescription(avatar, imageView);
            if (bm != null) {
                cancelPotentialWork(avatar, imageView);
                imageView.setImageDrawable(bm);
                imageView.setBackgroundColor(0x00000000);
                if (Build.VERSION.SDK_INT >= 28 && bm instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) bm).start();
                }
            } else {
                imageView.setBackgroundColor(avatar.getAvatarBackgroundColor());
                imageView.setImageDrawable(null);
                final AvatarWorkerTask task = new AvatarWorkerTask(imageView, size);
                final AsyncDrawable asyncDrawable =
                        new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(avatar);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    private static void setContentDescription(
            final AvatarService.Avatar avatar, final ImageView imageView) {
        final Context context = imageView.getContext();
        if (avatar instanceof Account) {
            imageView.setContentDescription(context.getString(R.string.your_avatar));
        } else if (avatar instanceof Message m && m.getType() == Message.TYPE_STATUS) {
            imageView.setContentDescription(null);
            return;
        } else {
            imageView.setContentDescription(
                    context.getString(R.string.avatar_for_x, avatar.getDisplayName()));
        }
    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<AvatarWorkerTask> avatarWorkerTaskReference;

        AsyncDrawable(Resources res, Bitmap bitmap, AvatarWorkerTask workerTask) {
            super(res, bitmap);
            avatarWorkerTaskReference = new WeakReference<>(workerTask);
        }

        AvatarWorkerTask getAvatarWorkerTask() {
            return avatarWorkerTaskReference.get();
        }
    }
}
