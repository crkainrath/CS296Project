package com.cs296.kainrath.cs296project.backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kainrath on 4/20/16.
 * Class for sending List<ChatGroup> to the app from the endpoint
 */

public class ChatGroupList {
    private List<ChatGroup> chatGroups;

    public ChatGroupList(List<ChatGroup> chatGroups) {
        this.chatGroups = chatGroups;
    }

    public List<ChatGroup> getChatGroups() {
        return chatGroups;
    }
}
