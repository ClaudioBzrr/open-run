package com.openrun.app.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.openrun.app.OpenRunApp;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;

import java.util.List;

public class HistoryViewModel extends AndroidViewModel {
    private final SessionRepository repository;

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        repository = ((OpenRunApp) application).getRepository();
    }

    public LiveData<List<SessionEntity>> getSessions() {
        return repository.observeFinishedSessions();
    }
}
