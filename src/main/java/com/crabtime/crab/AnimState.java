package com.crabtime.crab;

/** Which looping animation a crab is showing on the client, and the one it's blending out of. */
public final class AnimState {
    public String cur, prev;
    public float curStart, prevStart, switchAt = -100;
}
