package com.permitping.domain;

public enum ProfileType {
    COMPANY, WORKER;
    public String label() { return name().substring(0, 1) + name().substring(1).toLowerCase(); }
}
