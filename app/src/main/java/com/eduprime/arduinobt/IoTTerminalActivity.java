package com.eduprime.arduinobt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DatabaseReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class IoTTerminalActivity extends BaseActivity {

    private final List<String> history = new ArrayList<>();
    private HistoryAdapter adapter;
    private TextView lastSentLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot_terminal);

        String uid = getIntent().getStringExtra("userGeneratedUID");
        if (uid == null) { finish(); return; }

        DatabaseReference terminalRef = IoTFirebaseManager.getDatabase(this)
                .getReference("commands/" + uid + "/terminal");

        lastSentLabel = findViewById(R.id.lastSentLabel);
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.clearBtn).setOnClickListener(v -> {
            history.clear();
            adapter.notifyDataSetChanged();
            lastSentLabel.setText("—");
        });

        RecyclerView recycler = findViewById(R.id.historyRecycler);
        adapter = new HistoryAdapter(history);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setReverseLayout(true);
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);

        EditText input   = findViewById(R.id.commandInput);
        ImageButton send = findViewById(R.id.sendBtn);

        Runnable sendAction = () -> {
            String cmd = input.getText().toString().trim();
            if (cmd.isEmpty()) return;
            terminalRef.setValue(cmd);
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String entry = "[" + ts + "] " + cmd;
            history.add(0, entry);
            adapter.notifyItemInserted(0);
            recycler.scrollToPosition(0);
            lastSentLabel.setText(cmd);
            input.setText("");
        };

        send.setOnClickListener(v -> sendAction.run());
        input.setOnEditorActionListener((v, actionId, event) -> { sendAction.run(); return true; });
    }

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
        private final List<String> items;
        HistoryAdapter(List<String> items) { this.items = items; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_iot_terminal_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            holder.text.setText(items.get(position));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView text;
            VH(View v) { super(v); text = v.findViewById(R.id.entryText); }
        }
    }
}
