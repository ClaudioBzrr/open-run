package com.openrun.app.ui;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.openrun.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    @Test
    public void homeInflatesStartActions() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.card_start_run));
                assertNotNull(activity.findViewById(R.id.card_start_walk));
                assertNotNull(activity.findViewById(R.id.button_backup_export));
                assertNotNull(activity.findViewById(R.id.button_backup_import));
            });
        }
    }
}
