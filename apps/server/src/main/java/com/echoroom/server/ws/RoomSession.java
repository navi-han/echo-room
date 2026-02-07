package com.echoroom.server.ws;

public interface RoomSession {

    String id();

    boolean isOpen();

    void send(String text);
}
