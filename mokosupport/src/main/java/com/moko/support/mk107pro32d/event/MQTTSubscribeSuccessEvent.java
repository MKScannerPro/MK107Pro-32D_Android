package com.moko.support.mk107pro32d.event;

public class MQTTSubscribeSuccessEvent {
    private String topic;

    public MQTTSubscribeSuccessEvent(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
