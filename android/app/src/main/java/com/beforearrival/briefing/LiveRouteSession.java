package com.beforearrival.briefing;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** The foreground UI and active audio queue share one NaviHelper connection on the main thread. */
final class LiveRouteSession implements RouteInput {
    static final LiveRouteSession shared = new LiveRouteSession();
    private final Set<Consumer<Snapshot>> observers = new LinkedHashSet<>();
    private final NavigationRouteInput input = new NavigationRouteInput(this::publish);
    private RouteConnection connection;

    void attach(Context context, Consumer<Snapshot> observer) {
        observers.add(observer);
        if (connection == null) reconnect(context);
        else observer.accept(snapshot());
    }

    void detach(Consumer<Snapshot> observer) {
        if (observers.remove(observer) && observers.isEmpty() && connection != null) {
            RouteConnection previous = connection;
            connection = null;
            previous.close();
        }
    }

    void reconnect(Context context) {
        if (connection != null) connection.close();
        connection = LiveConnectionFactory.create(context, input);
        connection.start();
    }

    @Override public Snapshot snapshot() { return input.snapshot(); }

    private void publish(Snapshot snapshot) {
        for (Consumer<Snapshot> observer : new ArrayList<>(observers)) {
            if (observers.contains(observer)) observer.accept(snapshot);
        }
    }

    private LiveRouteSession() {}
}
