package com.osean.opencodephone;

final class PtyNative {
    static { System.loadLibrary("phonepty"); }
    static native int[] spawn(String directory);
    static native int read(int fd,byte[] bytes);
    static native int write(int fd,byte[] bytes);
    static native void resize(int fd,int cols,int rows);
    static native void close(int fd,int pid);
    private PtyNative() {}
}
