package com.beforearrival.briefing;

/** One SDK instance per topic; no callback may be reused for a later request. */
interface ComparisonSpeech {
    void start(String text);
    void stop(Runnable stopped);
    void release();
}
