package com.beforearrival.briefing;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.function.Consumer;

import com.beforearrival.briefing.AccessPolicy.State;

final class CarRestrictions {
    // PARKED_USE_ALLOWED describes platform UX permission, not the gear position.

    private final Consumer<State> onChange;
    private Car car;
    private CarUxRestrictionsManager manager;
    private boolean closed;

    CarRestrictions(Consumer<State> onChange) {
        this.onChange = onChange;
    }

    void connect(Context context) {
        try {
            car = Car.createCar(context, new Handler(Looper.getMainLooper()),
                    Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, this::onServiceChanged);
        } catch (RuntimeException | LinkageError error) {
            Log.w("BeforeArrival", "AAOS restrictions unavailable", error);
            onChange.accept(State.UNKNOWN);
        }
    }

    private void onServiceChanged(Car connectedCar, boolean ready) {
        if (closed) return;
        unregister();
        onChange.accept(State.UNKNOWN);
        if (!ready) return;
        try {
            manager = (CarUxRestrictionsManager) connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
            if (manager == null) return;
            CarUxRestrictionsManager current = manager;
            current.registerListener(restrictions -> {
                if (!closed && manager == current) read(restrictions);
            });
            read(current.getCurrentCarUxRestrictions());
        } catch (RuntimeException error) {
            unregister();
            Log.w("BeforeArrival", "AAOS restriction state unavailable", error);
            onChange.accept(State.UNKNOWN);
        }
    }

    private void read(CarUxRestrictions restrictions) {
        if (restrictions == null) {
            onChange.accept(State.UNKNOWN);
        } else {
            onChange.accept((restrictions.isRequiresDistractionOptimization()
                    || restrictions.getActiveRestrictions() != CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                    ? State.RESTRICTED : State.PARKED_USE_ALLOWED);
        }
    }

    void close() {
        closed = true;
        unregister();
        onChange.accept(State.UNKNOWN);
        if (car != null) {
            try { car.disconnect(); }
            catch (RuntimeException error) { Log.w("BeforeArrival", "Car already disconnected", error); }
            car = null;
        }
    }

    private void unregister() {
        if (manager == null) return;
        CarUxRestrictionsManager previous = manager;
        manager = null;
        try {
            previous.unregisterListener();
        } catch (RuntimeException error) {
            Log.w("BeforeArrival", "AAOS restriction listener already unavailable", error);
        }
    }
}
