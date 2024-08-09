package com.moko.support.mk107pro32d.event;

public class MQTTSubscribeFailureEvent {
    private String topic;

    public MQTTSubscribeFailureEvent(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
