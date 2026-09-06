package com.teslaowls.teslaled.model;

public class Frame {

    public final byte[] ppmBytes;
    public final int durationMs;

    public Frame(byte[] ppmBytes, int durationMs) {
        this.ppmBytes = ppmBytes;
        this.durationMs = durationMs;
    }
}
