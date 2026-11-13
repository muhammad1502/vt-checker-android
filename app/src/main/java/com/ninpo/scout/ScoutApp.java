package com.ninpo.scout;

import android.app.Application;

/** Applies the saved Appearance before any screen or dialog is created. */
public class ScoutApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Appearance.apply(this);
    }
}
