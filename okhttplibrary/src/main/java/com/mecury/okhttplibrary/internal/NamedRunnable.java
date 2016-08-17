package com.mecury.okhttplibrary.internal;

/**
 * Runnable implementation which always sets its thread name.
 * 线程继承的抽象类
 */
public abstract class NamedRunnable implements Runnable {

    protected final String name;

    public NamedRunnable(String format, Object... args) {
        this.name = Util.format(format, args);
    }

    @Override
    public final void run() {
        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName(name);
        try{
            execute();
        } finally {
            Thread.currentThread().setName(oldName);
        }
    }

    protected abstract void execute();
}
