package com.litus_animae.refitted.data.room;

import android.content.Context;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class ContextWeakReference {

    private final WeakReference<Context> context;

    public ContextWeakReference(WeakReference<Context> context) {
        this.context = context;
    }

    public ContextWeakReference(Context context) {
        this.context = new WeakReference<>(context.getApplicationContext());
    }

    @Override
    public int hashCode() {
        return Objects.hash(context.get());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof ContextWeakReference) {
            return ((ContextWeakReference) obj).context.get().equals(context.get());
        } else {
            return false;
        }
    }

    public Context get() {
        return context.get();
    }
}
