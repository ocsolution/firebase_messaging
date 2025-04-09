package io.flutter.plugins.firebase.messaging;

import java.util.Map;

public class MyMessageData {
    public final String type;
    public final String senderId;
    public final String senderName;
    public final String senderAvatar;
    public final String groupName;
    public final String groupId;
    public final String groupAvatar;
    public final String message;
    public final String icon;
    public final String imageUrl;

    public MyMessageData(Map<String, String> data) {
        this.type = data.get("type");

        this.groupId = data.get("group_id");
        this.groupName = data.get("group_name");
        this.groupAvatar = data.get("group_avatar");

        this.senderId = data.get("sender_id");
        this.senderName = data.get("sender_name");
        this.senderAvatar = data.get("avatar");

        this.message = data.get("message");
        this.icon = data.get("icon");
        this.imageUrl = data.get("image_url");
    }
}
