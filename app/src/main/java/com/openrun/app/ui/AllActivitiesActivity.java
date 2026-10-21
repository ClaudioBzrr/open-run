package com.openrun.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.openrun.app.OpenRunApp;
import com.openrun.app.R;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.databinding.ActivityAllActivitiesBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AllActivitiesActivity extends AppCompatActivity {
    public static final String EXTRA_SHOW_ALL =
            "com.openrun.app.extra.SHOW_ALL_ACTIVITIES";

    private static final int RECENT_LIMIT = 4;

    private ActivityAllActivitiesBinding binding;
    private SessionAdapter adapter;
    private SessionRepository repository;
    private List<SessionEntity> allSessions = Collections.emptyList();
    private boolean showAll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAllActivitiesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        showAll = getIntent().getBooleanExtra(EXTRA_SHOW_ALL, false);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.all_activities);
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        repository = ((OpenRunApp) getApplication()).getRepository();
        adapter = new SessionAdapter(this::openDetail);
        binding.recyclerSessions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSessions.setAdapter(adapter);

        ItemTouchHelper swipe = new ItemTouchHelper(
                new SessionSwipeCallback(this, this::onSessionSwiped)
        );
        swipe.attachToRecyclerView(binding.recyclerSessions);

        HistoryViewModel viewModel = new ViewModelProvider(this)
                .get(HistoryViewModel.class);
        viewModel.getSessions().observe(this, sessions -> {
            allSessions = sessions == null
                    ? Collections.emptyList()
                    : sessions;
            applyList();
        });
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_view_all) {
            showAll = true;
            applyList();
            return true;
        }
        return false;
    }

    private void applyList() {
        List<SessionEntity> visible;
        if (!showAll && allSessions.size() > RECENT_LIMIT) {
            visible = new ArrayList<>(
                    allSessions.subList(0, RECENT_LIMIT)
            );
        } else {
            visible = allSessions;
        }
        adapter.submitList(visible);
        boolean empty = visible.isEmpty();
        binding.emptyHistory.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerSessions.setVisibility(empty ? View.GONE : View.VISIBLE);
        MenuItem viewAll = binding.toolbar.getMenu().findItem(R.id.action_view_all);
        if (viewAll != null) {
            viewAll.setVisible(!showAll && allSessions.size() > RECENT_LIMIT);
        }
    }

    private void openDetail(SessionEntity session) {
        Intent intent = new Intent(this, SessionDetailActivity.class);
        intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, session.id);
        startActivity(intent);
    }

    private void onSessionSwiped(int position) {
        List<SessionEntity> sessions = adapter.getCurrentList();
        if (position < 0 || position >= sessions.size()) {
            return;
        }
        SessionEntity session = sessions.get(position);
        adapter.notifyItemChanged(position);
        SessionDeleteDialog.confirm(this, repository, session);
    }
}
