package com.openrun.app.ui;

import android.content.Intent;
import android.os.Bundle;
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

import java.util.List;

public class AllActivitiesActivity extends AppCompatActivity {
    private ActivityAllActivitiesBinding binding;
    private SessionAdapter adapter;
    private SessionRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAllActivitiesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
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
            adapter.submitList(sessions);
            boolean empty = sessions == null || sessions.isEmpty();
            binding.emptyHistory.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerSessions.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
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
