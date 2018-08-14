package org.jcollectd.agent.api;

public interface Packet<T> extends Identifier {
    void setInterval(long interval);
    long getInterval();
    void setHires(boolean hires);
    boolean isHires();
    T getData();
}
