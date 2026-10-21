package com.openrun.app.ui;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.openrun.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class AllActivitiesActivityTest {
    @Test
    public void screenInflatesWithToolbarAndList() {
        try (ActivityScenario<AllActivitiesActivity> scenario =
                     ActivityScenario.launch(AllActivitiesActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.toolbar));
                assertNotNull(activity.findViewById(R.id.recycler_sessions));
                assertNotNull(activity.findViewById(R.id.empty_history));
            });
        }
    }
}
