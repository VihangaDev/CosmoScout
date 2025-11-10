package com.cosmoscout.data;

public abstract class Result<T> {
    private Result() {}

    public static final class Ok<T> extends Result<T> {
        public final T value;

        public Ok(T v) {
            this.value = v;
        }
    }

    public static final class Err<T> extends Result<T> {
        public final Throwable error;

        public Err(Throwable e) {
            this.error = e;
        }
    }
}
