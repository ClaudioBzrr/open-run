package com.openrun.app.ui;

import android.view.MenuItem;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.navigation.NavigationView;
import com.openrun.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    @Test
    public void homeInflatesNavigationAndStartActions() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.card_start_run));
                assertNotNull(activity.findViewById(R.id.card_start_walk));
                assertNotNull(activity.findViewById(R.id.button_menu));
                assertNotNull(activity.findViewById(R.id.button_view_all));

                DrawerLayout drawer = activity.findViewById(R.id.drawer_layout);
                NavigationView navigation = activity.findViewById(R.id.navigation_view);
                assertNotNull(drawer);
                assertNotNull(navigation);
                assertNotNull(navigation.getMenu().findItem(R.id.nav_all_activities));
                assertNotNull(navigation.getMenu().findItem(R.id.nav_backup_export));
                assertNotNull(navigation.getMenu().findItem(R.id.nav_backup_import));
                MenuItem language = navigation.getMenu().findItem(R.id.nav_language);
                assertNotNull(language);
                assertNotNull(language.getSubMenu());
                assertNotNull(language.getSubMenu().findItem(R.id.nav_language_pt));
            });
        }
    }
}
