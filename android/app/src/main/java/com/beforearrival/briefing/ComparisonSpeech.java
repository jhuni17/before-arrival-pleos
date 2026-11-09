package com.beforearrival.briefing;

/** One SDK instance per topic: the public SDK has no utterance correlation token. */
interface ComparisonSpeech {
    void start(String text);
    void stop(Runnable stopped);
    void release();
}
