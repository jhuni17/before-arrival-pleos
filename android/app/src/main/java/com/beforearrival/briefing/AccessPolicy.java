package com.beforearrival.briefing;

/** Navigation duration never authorizes vehicle interaction or speech. */
final class AccessPolicy {
    enum State { UNKNOWN, PARKED_USE_ALLOWED, RESTRICTED }

    static boolean canInteract(boolean resumed, State state) {
        return resumed && state == State.PARKED_USE_ALLOWED;
    }
    private AccessPolicy() {}
}
