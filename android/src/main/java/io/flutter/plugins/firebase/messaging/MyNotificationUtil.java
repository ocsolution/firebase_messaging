package io.flutter.plugins.firebase.messaging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.firebase.messaging.RemoteMessage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


public class MyNotificationUtil {
    private static final int MAX_IMAGE_SIZE_BYTES = 1024 * 1024;
    private static FlutterFirebaseMessagingStore store = FlutterFirebaseMessagingStore.getInstance();
    private static List<Map<String, String>> tmepData = new ArrayList<>();
    private static final String TAG = "MyFirebaseMsgService";
    public static String CHANNEL_CHAT_ID = "default_chat_notification_channel_id";
    public static String CHANNEL_CHAT_NAME = "Chats";

    public static void displayNotification(Context context, RemoteMessage remoteMessage) {
        MyMessageData messageData = new MyMessageData(remoteMessage.getData());

        // Default notification
        if (messageData.type == null || !Objects.equals(messageData.type, "chat"))
            return;

        int notifyId = createNotifyID();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create chat channel
        createChatChannel(notificationManager);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StatusBarNotification[] managers = notificationManager.getActiveNotifications();

            if (managers.length == 0)
                tmepData.clear();

            for (StatusBarNotification notification : managers) {
                int id = notification.getId();
                String messageId = store.getPreferencesStringValue("" + id, null);
                MyMessageData storeMessageData = new MyMessageData(messageDataStore(messageId));
                boolean isGroup = messageData.groupId != null && Objects.equals(messageData.groupId, storeMessageData.groupId);
                boolean isPersonal = messageData.groupId == null && storeMessageData.groupId == null && Objects.equals(messageData.senderId, storeMessageData.senderId);

                if (isGroup || isPersonal) {
                    Map<String, String> _newObj = new HashMap<>();

                    _newObj.put("notify_id", "" + id);
                    _newObj.put("message_id", messageId);

                    tmepData.add(_newObj);
                    notifyId = id;
                }
            }

            Intent intent = createIntent(context, remoteMessage);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, notifyId, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            Notification builder = chatNotificationBuilder(context, messageData, intent, pendingIntent, notifyId);

            store.setPreferencesStringValue("" + notifyId, remoteMessage.getMessageId());
            notificationManager.notify(notifyId, builder);
        }
    }

    public static void createChatChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_CHAT_ID, CHANNEL_CHAT_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private static Notification chatNotificationBuilder(Context context, MyMessageData messageData, Intent intent, PendingIntent pendingIntent, int notifyId) {
        Person defaultPerson = createDefaultPerson();
        boolean isGroup = messageData.groupId != null;
        String notificationId = "" + (isGroup ? messageData.groupId : messageData.senderId);
        String notificationLabel = "" + (isGroup ? messageData.groupName : messageData.senderName);

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(defaultPerson);

        messagingStyle.setGroupConversation(isGroup);

        for (Map<String, String> entry : tmepData) {
            if (("" + notifyId).equals(entry.get("notify_id"))) {
                Map<String, String> mData = messageDataStore(entry.get("message_id"));
                MyMessageData storeMessageData = new MyMessageData(mData);

                if (storeMessageData.imageUrl != null)
                    messagingStyle.addMessage("Photo", System.currentTimeMillis(), createPerson(storeMessageData));

                messagingStyle.addMessage(storeMessageData.message, System.currentTimeMillis(), createPerson(storeMessageData));
            }
        }

        if (messageData.imageUrl != null)
            messagingStyle.addMessage("Photo", System.currentTimeMillis(), createPerson(messageData));

        messagingStyle.addMessage(messageData.message, System.currentTimeMillis(), createPerson(messageData));

        ShortcutInfoCompat.Builder shortcut = new ShortcutInfoCompat.Builder(context, notificationId);

        shortcut.setLongLived(true);
        shortcut.setShortLabel(notificationLabel);
        shortcut.setIntent(intent);

        if (isGroup && messageData.groupAvatar != null)
            shortcut.setIcon(IconCompat.createWithBitmap(createAvatar(messageData.groupAvatar, null)));

        if (!isGroup)
            shortcut.setIcon(IconCompat.createWithBitmap(createAvatar(messageData.senderAvatar, messageData.senderName)));

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut.build());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_CHAT_ID);
        boolean isForeground = FlutterFirebaseMessagingUtils.isApplicationForeground(context);

        String icon = messageData.icon;

        if (icon == null) icon = "notification_icon";

        builder.setStyle(messagingStyle);
        builder.setSmallIcon(getIcon(context, icon));
        builder.setAutoCancel(true);
        builder.setGroup(notificationId);
        builder.setContentIntent(pendingIntent);
        builder.setOnlyAlertOnce(false);
        builder.setSilent(isForeground);
        builder.setShortcutInfo(shortcut.build());

        return builder.build();
    }

    private static Intent createIntent(Context context, RemoteMessage remoteMessage) {
        Intent intent = new Intent();
        Map<String, String> data = remoteMessage.getData();

        for (String key : data.keySet()) {
            intent.putExtra(key, data.get(key));
        }

        intent.putExtra("google.message_id", remoteMessage.getMessageId());
        intent.setAction("com.google.firebase.MESSAGING_EVENT");
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(context.getPackageName(), context.getPackageName() + ".MainActivity"));
        return intent;
    }

    private static int getIcon(Context context, String key) {
        Resources resources = context.getResources();
        String resourceKey = "ic_launcher";
        String defType = "mipmap";
        String pkgName = context.getPackageName();

        if (key != null) {
            defType = "drawable";
            resourceKey = key;
        }

        return resources.getIdentifier(resourceKey, defType, pkgName);
    }

    private static Person createPerson(MyMessageData messageData) {
        String url = messageData.senderAvatar;
        String name = messageData.senderName;
        String uid = messageData.senderId != null ? messageData.senderId : UUID.randomUUID().toString();
        Person.Builder person = new Person.Builder();
        Bitmap avatar = createAvatar(url, name);

        person.setIcon(IconCompat.createWithBitmap(avatar));
        person.setName(name);
        person.setKey(uid);

        return person.build();
    }

    private static Person createDefaultPerson() {
        Person.Builder person = new Person.Builder();
        person.setName("mobile.android");
        person.setKey("00000000000-000000000000-000000000-00000000");
        return person.build();
    }

    private static Map<String, String> messageDataStore(String messageId) {
        return (Map<String, String>) ((Map<String, Object>) store
                .getFirebaseMessageMap(messageId)
                .get("message"))
                .get("data");
    }

    private static Bitmap getBitmapFromUrl(String imageUrl) {
        try (InputStream inputStream = new BufferedInputStream(new MyLimitedInputStream(new URL(imageUrl).openStream(), MAX_IMAGE_SIZE_BYTES))) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Bitmap createRoundedBitmap(String imageUrl) {
        Bitmap bitmap = getBitmapFromUrl(imageUrl);

        if (bitmap == null) return null;

        return createRoundedImage(bitmap);
    }

    private static Bitmap createRoundedImage(Bitmap bitmap) {
        if (bitmap == null) return null;

        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, size, size);
        final RectF rectF = new RectF(rect);
        final float roundPx = size / 2f;

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        // Calculate the offsets for centering the image
        int left = (size - bitmap.getWidth()) / 2;
        int top = (size - bitmap.getHeight()) / 2;
        int right = left + bitmap.getWidth();
        int bottom = top + bitmap.getHeight();
        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Rect destRect = new Rect(left, top, right, bottom);

        canvas.drawBitmap(bitmap, srcRect, destRect, paint);

        return output;
    }

    private final static AtomicInteger c = new AtomicInteger(0);

    public static int createNotifyID() {
        return c.incrementAndGet();
    }

    private static Bitmap createAvatar(String url, String name) {
        Bitmap avatar = createRoundedBitmap(url);

        if (avatar == null)
            avatar = createBitmapWithText(firstLetter(name != null ? name : "A"));

        return avatar;
    }

    public static Bitmap createBitmapWithText(String text) {
        int size = 70;

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);

        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(generateColorFromText(text));
        canvas.drawRect(0, 0, size, size, backgroundPaint);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);

        float originalTextSize = 40;

        float newTextSize = originalTextSize * 0.8f;
        textPaint.setTextSize(newTextSize);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        Rect textBounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), textBounds);
        float textWidth = textBounds.width();

        float x = (size - textWidth) / 2;

        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float y = size / 2 - (fontMetrics.ascent + fontMetrics.descent) / 2;

        canvas.drawText(text, x, y, textPaint);

        return createRoundedImage(bitmap);
    }

    private static int generateColorFromText(String text) {
        Random random = new Random((int) text.charAt(0));
        int red = random.nextInt(256);
        int green = random.nextInt(256);
        int blue = random.nextInt(256);

        return Color.rgb(red, green, blue);
    }


    private static String firstLetter(String text) {
        char firstCharacter = text.charAt(0);
        char firstCharacterLowercase = Character.toUpperCase(firstCharacter);

        return String.valueOf(firstCharacterLowercase);
    }
}


