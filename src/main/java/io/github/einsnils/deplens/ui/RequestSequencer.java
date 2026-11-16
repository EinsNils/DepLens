package io.github.einsnils.deplens.ui;

import java.util.concurrent.atomic.AtomicLong;

/** Merkt sich die zuletzt gestartete Anfrage, damit veraltete Ergebnisse verworfen werden. */
public final class RequestSequencer {
    private final AtomicLong current = new AtomicLong();

    public long next() {
        return current.incrementAndGet();
    }

    public boolean isCurrent(long id) {
        return current.get() == id;
    }
}
