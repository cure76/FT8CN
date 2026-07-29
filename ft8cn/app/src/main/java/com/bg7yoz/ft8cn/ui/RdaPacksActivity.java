package com.bg7yoz.ft8cn.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.rda.RdaPackManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full RDA pack catalog: search, download, delete. Opened from Settings summary.
 */
public class RdaPacksActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Button refreshButton;
    private LinearLayout listLayout;
    private EditText searchEdit;
    private RdaPackManager.Catalog catalog;
    private String filterQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rda_packs);

        ImageButton back = findViewById(R.id.rdaPacksBackButton);
        refreshButton = findViewById(R.id.rdaPacksRefreshButton);
        listLayout = findViewById(R.id.rdaPacksListLayout);
        searchEdit = findViewById(R.id.rdaPacksSearchEdit);

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshCatalog(true);
            }
        });
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterQuery = s != null ? s.toString().trim() : "";
                renderRows();
            }
        });

        renderRows();
        refreshCatalog(false);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void refreshCatalog(final boolean showErrors) {
        refreshButton.setEnabled(false);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final RdaPackManager.Catalog loaded = RdaPackManager.fetchCatalog();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) {
                                return;
                            }
                            catalog = loaded;
                            refreshButton.setEnabled(true);
                            renderRows();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) {
                                return;
                            }
                            refreshButton.setEnabled(true);
                            if (showErrors) {
                                ToastMessage.show(String.format(
                                        getString(R.string.rda_packs_catalog_fail),
                                        e.getMessage() != null ? e.getMessage() : e.toString()));
                            }
                            renderRows();
                        }
                    });
                }
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void renderRows() {
        listLayout.removeAllViews();
        if (catalog == null) {
            TextView hint = new TextView(this);
            hint.setTextColor(getColor(R.color.text_view_color));
            hint.setTextSize(12f);
            hint.setText(getString(R.string.rda_packs_refresh));
            listLayout.addView(hint);
            return;
        }

        List<RdaPackManager.PackInfo> visible = new ArrayList<>();
        for (RdaPackManager.PackInfo pack : catalog.packs) {
            if (pack.builtinInApk) {
                continue;
            }
            if (!matchesFilter(pack)) {
                continue;
            }
            visible.add(pack);
        }
        Collections.sort(visible, new Comparator<RdaPackManager.PackInfo>() {
            @Override
            public int compare(RdaPackManager.PackInfo a, RdaPackManager.PackInfo b) {
                boolean ai = RdaPackManager.isInstalled(RdaPacksActivity.this, a.id);
                boolean bi = RdaPackManager.isInstalled(RdaPacksActivity.this, b.id);
                if (ai != bi) {
                    return ai ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });

        if (visible.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setTextColor(getColor(R.color.text_view_color));
            empty.setTextSize(13f);
            empty.setPadding(0, 16, 0, 0);
            empty.setText(getString(R.string.rda_packs_empty_filter));
            listLayout.addView(empty);
            return;
        }

        for (final RdaPackManager.PackInfo pack : visible) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(this);
            title.setTextColor(getColor(R.color.text_view_color));
            title.setTextSize(13f);
            long kb = Math.max(1, (pack.bytes + 1023) / 1024);
            title.setText(pack.name + " ("
                    + String.format(getString(R.string.rda_packs_size_kb), (int) kb) + ")");

            TextView status = new TextView(this);
            status.setTextColor(getColor(R.color.text_view_color));
            status.setTextSize(12f);
            final boolean installed = RdaPackManager.isInstalled(this, pack.id);
            status.setText(installed
                    ? getString(R.string.rda_packs_installed)
                    : getString(R.string.rda_packs_not_installed));

            texts.addView(title);
            texts.addView(status);
            row.addView(texts);

            Button action = new Button(this);
            action.setTextSize(12f);
            action.setMinHeight(36);
            if (installed) {
                action.setText(R.string.rda_packs_delete);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        deletePack(pack.id);
                    }
                });
            } else {
                action.setText(R.string.rda_packs_download);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadPack(pack);
                    }
                });
            }
            row.addView(action);
            listLayout.addView(row);
        }
    }

    private boolean matchesFilter(RdaPackManager.PackInfo pack) {
        if (filterQuery.isEmpty()) {
            return true;
        }
        String q = filterQuery.toLowerCase(Locale.US);
        if (pack.name != null && pack.name.toLowerCase(Locale.US).contains(q)) {
            return true;
        }
        if (pack.id != null && pack.id.toLowerCase(Locale.US).contains(q)) {
            return true;
        }
        for (String prefix : pack.codesPrefix) {
            if (prefix != null && prefix.toLowerCase(Locale.US).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private void downloadPack(final RdaPackManager.PackInfo pack) {
        if (catalog == null) {
            return;
        }
        if (!RdaPackManager.isInstalled(this, pack.id)
                && RdaPackManager.downloadedCount(this) >= RdaPackManager.MAX_DOWNLOADED_PACKS) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.rda_packs_limit_title)
                    .setMessage(R.string.rda_packs_limit_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        refreshButton.setEnabled(false);
        final RdaPackManager.Catalog cat = catalog;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    RdaPackManager.installPack(getApplicationContext(), cat, pack);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) {
                                return;
                            }
                            refreshButton.setEnabled(true);
                            ToastMessage.show(getString(R.string.rda_packs_download_ok));
                            renderRows();
                        }
                    });
                } catch (final RdaPackManager.PackLimitException e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) {
                                return;
                            }
                            refreshButton.setEnabled(true);
                            new AlertDialog.Builder(RdaPacksActivity.this)
                                    .setTitle(R.string.rda_packs_limit_title)
                                    .setMessage(R.string.rda_packs_limit_message)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                            renderRows();
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing()) {
                                return;
                            }
                            refreshButton.setEnabled(true);
                            ToastMessage.show(String.format(
                                    getString(R.string.rda_packs_download_fail),
                                    e.getMessage() != null ? e.getMessage() : e.toString()));
                            renderRows();
                        }
                    });
                }
            }
        });
    }

    private void deletePack(final String packId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    RdaPackManager.deletePack(getApplicationContext(), packId);
                } catch (Exception e) {
                    // still refresh UI
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }
                        renderRows();
                    }
                });
            }
        });
    }
}
