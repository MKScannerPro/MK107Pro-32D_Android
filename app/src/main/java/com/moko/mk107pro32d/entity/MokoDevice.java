package com.moko.mk107pro32d.entity;


import java.io.Serializable;

public class MokoDevice implements Serializable {
    public int id;
    public String name;
    public String mac;
    public String mqttInfo;
    public int lwtEnable;
    public String lwtTopic;
    public String topicPublish;
    public String topicSubscribe;
    public boolean isOnline;
    public int deviceType;
    public int wifiRssi;
}
