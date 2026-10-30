package io.github.flymeisland.diagnostic;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Parcelable;
import java.io.ByteArrayOutputStream;

/**
 * Optional notification contact avatar -> bounded 48px PNG for a single
 * in-memory authenticated SystemUI event. No disk writes or logs.
 * May be absent if the source notification does not supply an avatar.
 */
final class NotificationAvatarExtractor {
    static final int MAX_BYTES = 32768;
    private NotificationAvatarExtractor() {}

    static byte[] fromNotification(Context c, Notification n) {
        if (n == null) return null;
        try {
            Icon icon = null;
            if (n.extras != null) {
                // Prefer the conversation icon where provided by the source app.
                Object conversation = n.extras.get("android.conversationIcon");
                if (conversation instanceof Icon) icon = (Icon) conversation;
                if (icon == null) {
                    Parcelable[] array = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                    if (array != null) {
                        java.util.List<Notification.MessagingStyle.Message> messages =
                                Notification.MessagingStyle.Message.getMessagesFromBundleArray(array);
                        for (int i = messages.size() - 1; i >= 0; i--) {
                            Person person = messages.get(i).getSenderPerson();
                            if (person != null && person.getIcon() != null) {
                                icon = person.getIcon();
                                break;
                            }
                        }
                    }
                }
                if (icon == null) {
                    Object raw = n.extras.get(Notification.EXTRA_MESSAGING_PERSON);
                    if (raw instanceof Person) icon = ((Person) raw).getIcon();
                }
            }
            if (icon == null) icon = n.getLargeIcon();
            if (icon == null) return null;
            Drawable drawable = icon.loadDrawable(c);
            if (drawable == null) return null;

            Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            drawable.setBounds(0, 0, 48, 48);
            drawable.draw(canvas);
            ByteArrayOutputStream out = new ByteArrayOutputStream(14000);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            bitmap.recycle();
            byte[] bytes = out.toByteArray();
            return bytes.length > 0 && bytes.length <= MAX_BYTES ? bytes : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
