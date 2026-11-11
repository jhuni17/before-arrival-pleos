package com.beforearrival.briefing;

/** One SDK instance per topic: the public SDK has no utterance correlation token. */
interface ComparisonSpeech {
    /** Main-thread snapshot: initialization, configuration and listener registration complete. */
    default boolean isReady() { return false; }
    void start(String text);
    void stop(Runnable stopped);
    void release();
}
