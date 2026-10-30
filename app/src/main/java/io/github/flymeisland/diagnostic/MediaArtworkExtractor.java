package io.github.flymeisland.diagnostic;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.service.notification.StatusBarNotification;
import java.io.ByteArrayOutputStream;

/** Bounded in-memory artwork from the selected media session or its media notification. */
final class MediaArtworkExtractor {
    private MediaArtworkExtractor() {}
    static byte[] get(MusicListenerService service, MediaMetadata metadata, String pkg) {
        int[] style = WaveStylePrefs.get(service);
        if (style[6] == 0 && style[8] == 0) return null;
        try {
            Bitmap image = null;
            if (metadata != null) {
                image=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if(image==null) image=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if(image==null) image=metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if(image==null && metadata.getDescription()!=null) image=metadata.getDescription().getIconBitmap();
            }
            if(image!=null && !image.isRecycled()) {
                Bitmap scaled=Bitmap.createScaledBitmap(image,64,64,true);
                ByteArrayOutputStream out=new ByteArrayOutputStream(16000);
                boolean ok=scaled.compress(Bitmap.CompressFormat.PNG,100,out);
                if(scaled!=image) scaled.recycle();
                byte[] data=out.toByteArray();
                if(ok && data.length>0 && data.length<=NotificationAvatarExtractor.MAX_BYTES) return data;
            }
            StatusBarNotification[] items=service.getActiveNotifications();
            if(items!=null) for(StatusBarNotification item:items) {
                if(item==null || !pkg.equals(item.getPackageName()) || item.getNotification()==null
                        || item.getNotification().extras==null
                        || !item.getNotification().extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) continue;
                byte[] data=NotificationAvatarExtractor.fromNotification(service,item.getNotification());
                if(data!=null) return data;
            }
        } catch (RuntimeException | OutOfMemoryError ignored) {}
        return null;
    }
}
