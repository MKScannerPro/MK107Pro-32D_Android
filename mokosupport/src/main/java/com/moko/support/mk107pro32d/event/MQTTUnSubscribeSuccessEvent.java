package com.moko.support.mk107pro32d.event;

public class MQTTUnSubscribeSuccessEvent {
    private String topic;

    public MQTTUnSubscribeSuccessEvent(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
