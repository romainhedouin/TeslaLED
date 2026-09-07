package com.teslaowls.teslaled.model;

/**
 * A continuously-updating panel message (current time, current speed, ...)
 * - as opposed to a fixed Frame sequence, this gets re-rendered on every
 * tick and keeps running until manually stopped.
 */
public interface LiveDataSource {
    byte[] renderFrame();
}
