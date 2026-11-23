package com.forscherfreunde.hub.service;

/** Fortschrittsmeldung aus Hintergrundaufgaben. {@code fraction < 0} bedeutet „unbestimmt“. */
@FunctionalInterface
public interface Progress {
    Progress NONE = (message, fraction) -> { };

    void update(String message, double fraction);
}
