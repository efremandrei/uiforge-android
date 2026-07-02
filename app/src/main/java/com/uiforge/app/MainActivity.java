package com.uiforge.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.uiforge.app.databinding.ActivityMainBinding;
import com.uiforge.app.model.UiComponent;
import com.uiforge.app.model.UiComponentType;
import com.uiforge.app.ui.LayerAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements LayerAdapter.LayerActionListener {
    private static final String LOG_TAG = "UIForge";
    private static final String FILE_LOG_NAME = "uidesignerFailLog.txt";
    private static final String FILE_LOG_PREFS = "uiforge_file_log";
    private static final String FILE_LOG_URI_PREF = "downloads_log_uri";
    private static final int MENU_HELP = 1001;
    private static final int MENU_ABOUT = 1002;
    private static final String STATE_PROJECT_NAME = "project_name";
    private static final String STATE_COMPONENTS = "components";
    private static final String STATE_SELECTION = "selection";
    private static final String PROJECTS_DIR = "projects";
    private static final String PROJECT_EXTENSION = ".uiforge.json";
    private static final int CANVAS_WIDTH_DP = 252;
    private static final int CANVAS_HEIGHT_DP = 520;
    private static final int MIN_WIDGET_WIDTH_DP = 48;
    private static final int MIN_WIDGET_HEIGHT_DP = 24;

    private ActivityMainBinding binding;
    private final List<UiComponent> components = new ArrayList<>();
    private final Map<String, Integer> palette = new LinkedHashMap<>();
    private final ExecutorService fileLogExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat fileLogDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private LayerAdapter layerAdapter;
    private boolean bindingInspector;
    private boolean dragInProgress;
    private boolean resizeHandlesVisible;
    private int selectedIndex = -1;
    private int dragTargetIndex = -1;
    private long lastMoveFileLogAtMs;
    private Uri cachedDownloadsLogUri;
    private Thread.UncaughtExceptionHandler previousUncaughtExceptionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashLogger();
        logDebug("onCreate start savedState=" + (savedInstanceState != null));
        configureSystemBars();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupSystemBarInsets();

        seedPalette();
        setupToolbar();
        setupLists();
        setupHelpButtons();
        setupPaletteButtons();
        setupTemplateButtons();
        setupInspector();
        setupProjectNameWatcher();
        setupPreviewCanvas();

        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        } else {
            applyTemplate(createCommerceTemplate(), "Commerce Checkout");
        }

        wireDropdowns();
        refreshAll();
        logDebug("onCreate ready components=" + components.size() + " selectedIndex=" + selectedIndex);
    }

    @Override
    protected void onDestroy() {
        logDebug("onDestroy finishing=" + isFinishing());
        fileLogExecutor.shutdown();
        super.onDestroy();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(ContextCompat.getColor(this, R.color.surface_base));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(true);
        logDebug("Configured system bars: black status bar, edgeToEdgeScrim=true sdk=" + Build.VERSION.SDK_INT);
    }

    private void setupSystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            binding.statusBarScrim.getLayoutParams().height = statusTop;
            binding.statusBarScrim.requestLayout();
            binding.getRoot().setPadding(0, 0, 0, navigationBottom);
            logDebug("Applied system insets statusTopPx=" + statusTop + " navBottomPx=" + navigationBottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void seedPalette() {
        palette.put("White", Color.parseColor("#FFFFFF"));
        palette.put("Sand", Color.parseColor("#F4E9DA"));
        palette.put("Ice", Color.parseColor("#EAF2F8"));
        palette.put("Cobalt", Color.parseColor("#1E3A5F"));
        palette.put("Mint", Color.parseColor("#2A9D8F"));
        palette.put("Sunset", Color.parseColor("#E76F51"));
        palette.put("Gold", Color.parseColor("#E9C46A"));
        palette.put("Charcoal", Color.parseColor("#24303D"));
        palette.put("Ink", Color.parseColor("#0E1A27"));
    }

    private void setupToolbar() {
        binding.toolbar.setTitle(getString(R.string.app_name));
        binding.toolbar.setSubtitle(null);
        binding.toolbar.getMenu().add(Menu.NONE, MENU_HELP, Menu.NONE, R.string.toolbar_help)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        binding.toolbar.getMenu().add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.toolbar_about)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_HELP) {
                showHelpScreen();
                return true;
            }
            if (item.getItemId() == MENU_ABOUT) {
                showAboutDialog();
                return true;
            }
            return false;
        });
        binding.saveProjectButton.setOnClickListener(v -> showSaveProjectDialog());
        binding.loadProjectButton.setOnClickListener(v -> showLoadProjectDialog());
        binding.exportButton.setOnClickListener(v -> showExportDialog());
    }

    private void setupHelpButtons() {
        configureHelpButton(binding.heroHelpButton, true);
        configureHelpButton(binding.paletteHelpButton, false);
        configureHelpButton(binding.templatesHelpButton, false);
        configureHelpButton(binding.previewHelpButton, false);
        configureHelpButton(binding.layersHelpButton, false);
        configureHelpButton(binding.inspectorHelpButton, false);
        binding.heroHelpButton.setOnClickListener(v -> showHelpScreen());
        binding.paletteHelpButton.setOnClickListener(v -> showHelpDialog(R.string.palette_title, R.string.help_palette));
        binding.templatesHelpButton.setOnClickListener(v -> showHelpDialog(R.string.templates_title, R.string.help_templates));
        binding.previewHelpButton.setOnClickListener(v -> showHelpDialog(R.string.preview_title, R.string.help_preview));
        binding.layersHelpButton.setOnClickListener(v -> showHelpDialog(R.string.layers_title, R.string.help_layers));
        binding.inspectorHelpButton.setOnClickListener(v -> showHelpDialog(R.string.inspector_title, R.string.help_inspector));
    }

    private void configureHelpButton(MaterialButton button, boolean onDarkBackground) {
        int color = ContextCompat.getColor(this, onDarkBackground ? R.color.text_on_dark : R.color.text_strong);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.TRANSPARENT);
        circle.setStroke(dp(2), color);
        button.setBackground(circle);
        button.setTextColor(color);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(0, 0, 0, 0);
    }

    private void setupLists() {
        binding.layersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        layerAdapter = new LayerAdapter(components, this);
        binding.layersRecyclerView.setAdapter(layerAdapter);
    }

    private void setupPaletteButtons() {
        configurePaletteButton(binding.addHeaderButton, UiComponentType.HEADER);
        configurePaletteButton(binding.addTextButton, UiComponentType.TEXT);
        configurePaletteButton(binding.addButtonButton, UiComponentType.BUTTON);
        configurePaletteButton(binding.addInputButton, UiComponentType.INPUT);
        configurePaletteButton(binding.addCardButton, UiComponentType.CARD);
        configurePaletteButton(binding.addImageButton, UiComponentType.IMAGE);
        configurePaletteButton(binding.addTabsButton, UiComponentType.TABS);
        configurePaletteButton(binding.addDropdownButton, UiComponentType.DROPDOWN);
        configurePaletteButton(binding.addCheckboxButton, UiComponentType.CHECKBOX);
        configurePaletteButton(binding.addSwitchButton, UiComponentType.SWITCH);
        configurePaletteButton(binding.addDividerButton, UiComponentType.DIVIDER);
        configurePaletteButton(binding.addProgressButton, UiComponentType.PROGRESS);
    }

    private void configurePaletteButton(MaterialButton button, UiComponentType type) {
        button.setOnClickListener(v -> Toast.makeText(this, R.string.drag_palette_toast, Toast.LENGTH_SHORT).show());
        button.setOnLongClickListener(v -> startDrag(v, new DragPayload(type, -1)));
    }

    private void setupTemplateButtons() {
        binding.templateCommerceButton.setOnClickListener(v -> applyTemplate(createCommerceTemplate(), "Commerce Checkout"));
        binding.templateProfileButton.setOnClickListener(v -> applyTemplate(createProfileTemplate(), "Profile Setup"));
        binding.templateOnboardingButton.setOnClickListener(v -> applyTemplate(createOnboardingTemplate(), "Onboarding Flow"));
    }

    private void setupProjectNameWatcher() {
        binding.projectNameInput.addTextChangedListener(simpleWatcher(
                s -> binding.previewProjectName.setText(nonEmpty(s, "Untitled Flow"))));
    }

    private void setupPreviewCanvas() {
        binding.previewCanvas.setOnDragListener(this::handlePreviewCanvasDrag);
    }

    private void wireDropdowns() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>(palette.keySet()));
        binding.backgroundColorInput.setAdapter(adapter);
        binding.accentColorInput.setAdapter(adapter);
        binding.backgroundColorInput.setOnItemClickListener((parent, view, position, id) ->
                updateSelected(component -> component.setBackgroundColorName((String) parent.getItemAtPosition(position))));
        binding.accentColorInput.setOnItemClickListener((parent, view, position, id) ->
                updateSelected(component -> component.setAccentColorName((String) parent.getItemAtPosition(position))));
    }

    private void setupInspector() {
        binding.componentTitleInput.addTextChangedListener(simpleWatcher(
                s -> updateSelected(component -> component.setTitle(s))));
        binding.componentHelperInput.addTextChangedListener(simpleWatcher(
                s -> updateSelected(component -> component.setHelper(s))));
        binding.fullWidthSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!bindingInspector) {
                updateSelected(component -> component.setFullWidth(isChecked));
            }
        });
        binding.emphasisSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!bindingInspector) {
                updateSelected(component -> component.setEmphasized(isChecked));
            }
        });
        binding.widthSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int width = progress + 30;
                binding.widthValueLabel.setText(getString(R.string.percent_value, width));
                if (fromUser) {
                    updateSelected(component -> {
                        component.setFullWidth(false);
                        component.setWidthPercent(width);
                    });
                }
            }
        });
        binding.heightSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int height = progress == 0 ? 0 : progress + 31;
                binding.heightValueLabel.setText(height == 0 ? getString(R.string.auto_value) : getString(R.string.dp_value, height));
                if (fromUser) {
                    updateSelected(component -> component.setHeightDp(height));
                }
            }
        });
        binding.textSizeSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int textSize = progress + 10;
                binding.textSizeValueLabel.setText(getString(R.string.sp_value, textSize));
                if (fromUser) {
                    updateSelected(component -> component.setTextSizeSp(textSize));
                }
            }
        });
        binding.paddingSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                binding.paddingValueLabel.setText(getString(R.string.dp_value, progress + 8));
                if (fromUser) {
                    updateSelected(component -> component.setPaddingDp(progress + 8));
                }
            }
        });
        binding.radiusSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                binding.radiusValueLabel.setText(getString(R.string.dp_value, progress + 4));
                if (fromUser) {
                    updateSelected(component -> component.setCornerRadiusDp(progress + 4));
                }
            }
        });
        binding.alignmentToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || bindingInspector) {
                return;
            }
            if (checkedId == R.id.alignStartButton) {
                updateSelected(component -> component.setAlignment("start"));
            } else if (checkedId == R.id.alignCenterButton) {
                updateSelected(component -> component.setAlignment("center"));
            } else if (checkedId == R.id.alignEndButton) {
                updateSelected(component -> component.setAlignment("end"));
            }
        });
        binding.deleteButton.setOnClickListener(v -> deleteSelected());
    }

    private void applyTemplate(List<UiComponent> template, String projectName) {
        logDebug("Applying template project=" + projectName + " count=" + template.size());
        components.clear();
        components.addAll(template);
        assignDefaultPositions(components);
        binding.projectNameInput.setText(projectName);
        selectedIndex = components.isEmpty() ? -1 : 0;
        resizeHandlesVisible = false;
        refreshAll();
    }

    private List<UiComponent> createCommerceTemplate() {
        List<UiComponent> template = new ArrayList<>();
        template.add(new UiComponent(UiComponentType.HEADER, "Curated summer drop", "Bold seasonal hero with a fast CTA", "Sand", "Cobalt", true, true, 22, 28, "start"));
        template.add(new UiComponent(UiComponentType.IMAGE, "Lifestyle banner", "Show the featured collection", "Charcoal", "Gold", true, false, 18, 28, "center"));
        template.add(new UiComponent(UiComponentType.CARD, "Best seller", "Short product proof, delivery promise, rating", "White", "Sunset", true, false, 18, 24, "start"));
        template.add(new UiComponent(UiComponentType.BUTTON, "Shop the edit", "", "Cobalt", "Sand", true, true, 18, 22, "center"));
        return template;
    }

    private List<UiComponent> createProfileTemplate() {
        List<UiComponent> template = new ArrayList<>();
        template.add(new UiComponent(UiComponentType.HEADER, "Create your creator profile", "Pick a voice, add links, set your public look", "Ice", "Ink", true, true, 22, 28, "start"));
        template.add(new UiComponent(UiComponentType.IMAGE, "Avatar cover", "Square or portrait image slot", "Cobalt", "Sand", true, false, 20, 30, "center"));
        template.add(new UiComponent(UiComponentType.INPUT, "Display name", "@yourbrand", "White", "Mint", true, false, 16, 18, "start"));
        template.add(new UiComponent(UiComponentType.INPUT, "Bio", "What should people know first?", "White", "Sunset", true, false, 16, 18, "start"));
        template.add(new UiComponent(UiComponentType.BUTTON, "Publish profile", "", "Mint", "White", true, true, 18, 22, "center"));
        return template;
    }

    private List<UiComponent> createOnboardingTemplate() {
        List<UiComponent> template = new ArrayList<>();
        template.add(new UiComponent(UiComponentType.HEADER, "Welcome to focus mode", "Guide the user into the first strong action", "Sand", "Cobalt", true, true, 22, 28, "center"));
        template.add(new UiComponent(UiComponentType.TEXT, "Why it matters", "One clear paragraph is often enough for onboarding", "White", "Ink", true, false, 18, 22, "center"));
        template.add(new UiComponent(UiComponentType.CARD, "Daily habit", "Use cards for checklists, insight blocks, and features", "Ice", "Mint", true, false, 18, 24, "start"));
        template.add(new UiComponent(UiComponentType.BUTTON, "Start first session", "", "Sunset", "White", true, true, 18, 22, "center"));
        return template;
    }

    private void refreshAll() {
        if (selectedIndex < 0 && !components.isEmpty()) {
            selectedIndex = 0;
        }
        if (selectedIndex >= components.size()) {
            selectedIndex = components.isEmpty() ? -1 : components.size() - 1;
        }
        binding.previewProjectName.setText(nonEmpty(textOf(binding.projectNameInput), "Untitled Flow"));
        renderPreview();
        layerAdapter.setSelectedPosition(selectedIndex);
        bindInspector();
    }

    private void bindInspector() {
        bindingInspector = true;
        UiComponent selected = getSelected();
        boolean enabled = selected != null;
        binding.selectedTypeLabel.setText(enabled ? selected.getType().getLabel() : getString(R.string.no_selection));
        binding.componentTitleInput.setEnabled(enabled);
        binding.componentHelperInput.setEnabled(enabled);
        binding.backgroundColorInput.setEnabled(enabled);
        binding.accentColorInput.setEnabled(enabled);
        binding.fullWidthSwitch.setEnabled(enabled);
        binding.emphasisSwitch.setEnabled(enabled);
        binding.paddingSeekBar.setEnabled(enabled);
        binding.radiusSeekBar.setEnabled(enabled);
        binding.widthSeekBar.setEnabled(enabled);
        binding.heightSeekBar.setEnabled(enabled);
        binding.textSizeSeekBar.setEnabled(enabled);
        binding.alignStartButton.setEnabled(enabled);
        binding.alignCenterButton.setEnabled(enabled);
        binding.alignEndButton.setEnabled(enabled);
        binding.deleteButton.setEnabled(enabled);

        if (selected != null) {
            binding.componentTitleInput.setText(selected.getTitle());
            binding.componentHelperInput.setText(selected.getHelper());
            binding.backgroundColorInput.setText(selected.getBackgroundColorName(), false);
            binding.accentColorInput.setText(selected.getAccentColorName(), false);
            binding.fullWidthSwitch.setChecked(selected.isFullWidth());
            binding.emphasisSwitch.setChecked(selected.isEmphasized());
            binding.widthSeekBar.setProgress(selected.getWidthPercent() - 30);
            binding.heightSeekBar.setProgress(selected.getHeightDp() == 0 ? 0 : selected.getHeightDp() - 31);
            binding.textSizeSeekBar.setProgress(selected.getTextSizeSp() - 10);
            binding.paddingSeekBar.setProgress(selected.getPaddingDp() - 8);
            binding.radiusSeekBar.setProgress(selected.getCornerRadiusDp() - 4);
            binding.widthValueLabel.setText(getString(R.string.percent_value, selected.getWidthPercent()));
            binding.heightValueLabel.setText(selected.getHeightDp() == 0 ? getString(R.string.auto_value) : getString(R.string.dp_value, selected.getHeightDp()));
            binding.textSizeValueLabel.setText(getString(R.string.sp_value, selected.getTextSizeSp()));
            binding.paddingValueLabel.setText(getString(R.string.dp_value, selected.getPaddingDp()));
            binding.radiusValueLabel.setText(getString(R.string.dp_value, selected.getCornerRadiusDp()));
            if ("center".equals(selected.getAlignment())) {
                binding.alignmentToggle.check(R.id.alignCenterButton);
            } else if ("end".equals(selected.getAlignment())) {
                binding.alignmentToggle.check(R.id.alignEndButton);
            } else {
                binding.alignmentToggle.check(R.id.alignStartButton);
            }
        } else {
            binding.componentTitleInput.setText("");
            binding.componentHelperInput.setText("");
            binding.backgroundColorInput.setText("", false);
            binding.accentColorInput.setText("", false);
            binding.widthValueLabel.setText(getString(R.string.percent_value, 0));
            binding.heightValueLabel.setText(getString(R.string.auto_value));
            binding.textSizeValueLabel.setText(getString(R.string.sp_value, 0));
            binding.paddingValueLabel.setText(getString(R.string.dp_value, 0));
            binding.radiusValueLabel.setText(getString(R.string.dp_value, 0));
            binding.alignmentToggle.clearChecked();
        }
        bindingInspector = false;
    }

    private void renderPreview() {
        binding.previewCanvas.removeAllViews();
        if (components.isEmpty() && !dragInProgress) {
            View empty = createEmptyCanvasState();
            binding.previewCanvas.addView(empty, fullCanvasLayoutParams());
            return;
        }
        for (int i = 0; i < components.size(); i++) {
            UiComponent component = components.get(i);
            clampComponentToCanvas(component);
            FrameLayout container = createCanvasItemContainer(component, i);
            binding.previewCanvas.addView(container, canvasItemLayoutParams(component));
        }
    }

    private FrameLayout createCanvasItemContainer(UiComponent component, int index) {
        FrameLayout container = new FrameLayout(this);
        container.setTag(Integer.valueOf(index));
        container.setClipChildren(false);
        container.setClipToPadding(false);
        View preview = createPreviewView(component, index == selectedIndex);
        preview.setTag(Integer.valueOf(index));
        installCanvasTouch(preview, index);
        installCanvasTouch(container, index);
        container.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (index == selectedIndex && resizeHandlesVisible) {
            addResizeHandles(container, index);
        }
        return container;
    }

    private FrameLayout.LayoutParams fullCanvasLayoutParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams canvasItemLayoutParams(UiComponent component) {
        int height = dp(resolveComponentHeightDp(component));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(resolveComponentWidthDp(component)), height);
        params.leftMargin = dp(component.getXdp());
        params.topMargin = dp(component.getYdp());
        return params;
    }

    private void installCanvasTouch(View view, int index) {
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        view.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startXdp;
            private int startYdp;
            private boolean moved;
            private boolean longPressed;
            private Runnable longPressAction;

            @Override
            public boolean onTouch(View touchedView, MotionEvent event) {
                try {
                if (index < 0 || index >= components.size()) {
                    return false;
                }
                UiComponent component = components.get(index);
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (resizeHandlesVisible && selectedIndex == index) {
                            clearResizeHandlesFromCanvas();
                            resizeHandlesVisible = false;
                            logDebug("Resize mode cleared by body drag index=" + index);
                        }
                        if (resizeHandlesVisible) {
                            clearResizeHandlesFromCanvas();
                            resizeHandlesVisible = false;
                        }
                        requestCanvasParentsDoNotIntercept(touchedView, true);
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startXdp = component.getXdp();
                        startYdp = component.getYdp();
                        moved = false;
                        longPressed = false;
                        selectedIndex = index;
                        layerAdapter.setSelectedPosition(selectedIndex);
                        bindInspector();
                        logDebug("Touch down index=" + index + " type=" + component.getType()
                                + " xDp=" + component.getXdp() + " yDp=" + component.getYdp()
                                + " widthDp=" + resolveComponentWidthDp(component)
                                + " heightDp=" + resolveComponentHeightDp(component));
                        longPressAction = () -> {
                            if (selectedIndex == index) {
                                longPressed = true;
                                resizeHandlesVisible = true;
                                logDebug("Long press resize mode index=" + index + " type=" + component.getType());
                                showResizeHandles(touchedView, index);
                            }
                        };
                        touchedView.postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        requestCanvasParentsDoNotIntercept(touchedView, true);
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!longPressed && (moved || Math.hypot(dx, dy) > touchSlop)) {
                            moved = true;
                            if (longPressAction != null) {
                                touchedView.removeCallbacks(longPressAction);
                            }
                            if (resizeHandlesVisible) {
                                clearResizeHandlesFromCanvas();
                                resizeHandlesVisible = false;
                            }
                            if (component.isFullWidth()) {
                                component.setWidthDp(defaultFreeWidthDp(component));
                                component.setFullWidth(false);
                            }
                            component.setXdp(startXdp + pxToDp(dx));
                            component.setYdp(startYdp + pxToDp(dy));
                            clampComponentToCanvas(component);
                            logDebug("Move index=" + index + " xDp=" + component.getXdp() + " yDp=" + component.getYdp());
                            View container = findCanvasContainer(touchedView);
                            if (container != null) {
                                positionContainer(container, component);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        requestCanvasParentsDoNotIntercept(touchedView, false);
                        if (longPressAction != null) {
                            touchedView.removeCallbacks(longPressAction);
                        }
                        if (!moved && !longPressed) {
                            if (resizeHandlesVisible) {
                                clearResizeHandlesFromCanvas();
                                resizeHandlesVisible = false;
                            }
                            logDebug("Tap selected index=" + index + " type=" + component.getType());
                        } else if (moved) {
                            bindInspector();
                        }
                        return true;
                    default:
                        return true;
                }
                } catch (Throwable throwable) {
                    logError("Canvas touch failed index=" + index + " action=" + event.getActionMasked(), throwable);
                    return true;
                }
            }
        });
    }

    private void addResizeHandles(FrameLayout container, int index) {
        addResizeHandle(container, index, "top_left", Gravity.TOP | Gravity.START);
        addResizeHandle(container, index, "top", Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        addResizeHandle(container, index, "top_right", Gravity.TOP | Gravity.END);
        addResizeHandle(container, index, "right", Gravity.CENTER_VERTICAL | Gravity.END);
        addResizeHandle(container, index, "bottom_right", Gravity.BOTTOM | Gravity.END);
        addResizeHandle(container, index, "bottom", Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        addResizeHandle(container, index, "bottom_left", Gravity.BOTTOM | Gravity.START);
        addResizeHandle(container, index, "left", Gravity.CENTER_VERTICAL | Gravity.START);
        logDebug("Resize handles shown index=" + index + " containerWidthPx=" + container.getWidth()
                + " containerHeightPx=" + container.getHeight());
    }

    private void addResizeHandle(FrameLayout container, int index, String direction, int gravity) {
        View handle = new View(this);
        handle.setTag("resize_handle");
        handle.setBackgroundResource(R.drawable.bg_resize_handle);
        handle.setClickable(true);
        handle.setElevation(dp(8));
        handle.setOnTouchListener(resizeHandleTouchListener(index, direction));
        int size = dp(28);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, gravity);
        container.addView(handle, params);
        handle.bringToFront();
    }

    private View.OnTouchListener resizeHandleTouchListener(int index, String direction) {
        return new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startXdp;
            private int startYdp;
            private int startWidthDp;
            private int startHeightDp;

            @Override
            public boolean onTouch(View handle, MotionEvent event) {
                try {
                if (index < 0 || index >= components.size()) {
                    return false;
                }
                UiComponent component = components.get(index);
                View container = findCanvasContainer(handle);
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        requestCanvasParentsDoNotIntercept(handle, true);
                        selectedIndex = index;
                        resizeHandlesVisible = true;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startXdp = component.getXdp();
                        startYdp = component.getYdp();
                        startWidthDp = Math.max(MIN_WIDGET_WIDTH_DP, resolveComponentWidthDp(component));
                        startHeightDp = resolveComponentHeightDp(component);
                        logDebug("Resize start index=" + index + " direction=" + direction
                                + " xDp=" + startXdp + " yDp=" + startYdp
                                + " widthDp=" + startWidthDp + " heightDp=" + startHeightDp);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        requestCanvasParentsDoNotIntercept(handle, true);
                        int dx = pxToDp(event.getRawX() - downRawX);
                        int dy = pxToDp(event.getRawY() - downRawY);
                        int nextX = startXdp;
                        int nextY = startYdp;
                        int nextWidth = startWidthDp;
                        int nextHeight = startHeightDp;
                        if (direction.contains("right")) {
                            nextWidth = startWidthDp + dx;
                        }
                        if (direction.contains("left")) {
                            nextWidth = startWidthDp - dx;
                            nextX = startXdp + dx;
                        }
                        if (direction.contains("bottom")) {
                            nextHeight = startHeightDp + dy;
                        }
                        if (direction.contains("top")) {
                            nextHeight = startHeightDp - dy;
                            nextY = startYdp + dy;
                        }
                        if (nextWidth < MIN_WIDGET_WIDTH_DP) {
                            if (direction.contains("left")) {
                                nextX -= MIN_WIDGET_WIDTH_DP - nextWidth;
                            }
                            nextWidth = MIN_WIDGET_WIDTH_DP;
                        }
                        int minHeight = minComponentHeightDp(component);
                        if (nextHeight < minHeight) {
                            if (direction.contains("top")) {
                                nextY -= minHeight - nextHeight;
                            }
                            nextHeight = minHeight;
                        }
                        component.setFullWidth(false);
                        component.setXdp(nextX);
                        component.setYdp(nextY);
                        component.setWidthDp(nextWidth);
                        component.setHeightDp(nextHeight);
                        clampComponentToCanvas(component);
                        logDebug("Resize move index=" + index + " direction=" + direction
                                + " xDp=" + component.getXdp() + " yDp=" + component.getYdp()
                                + " widthDp=" + resolveComponentWidthDp(component)
                                + " heightDp=" + resolveComponentHeightDp(component));
                        if (container != null) {
                            positionContainer(container, component);
                        }
                        bindInspector();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        requestCanvasParentsDoNotIntercept(handle, false);
                        logDebug("Resize end index=" + index + " direction=" + direction
                                + " xDp=" + component.getXdp() + " yDp=" + component.getYdp()
                                + " widthDp=" + resolveComponentWidthDp(component)
                                + " heightDp=" + resolveComponentHeightDp(component));
                        layerAdapter.setSelectedPosition(selectedIndex);
                        bindInspector();
                        return true;
                    default:
                        return true;
                }
                } catch (Throwable throwable) {
                    logError("Resize touch failed index=" + index + " direction=" + direction
                            + " action=" + event.getActionMasked(), throwable);
                    return true;
                }
            }
        };
    }

    private View findCanvasContainer(View view) {
        View current = view;
        while (current != null && current.getParent() instanceof View) {
            if (current.getParent() == binding.previewCanvas) {
                return current;
            }
            current = (View) current.getParent();
        }
        return view.getParent() == binding.previewCanvas ? view : null;
    }

    private void requestCanvasParentsDoNotIntercept(View source, boolean disallow) {
        View current = source;
        while (current != null && current.getParent() != null) {
            current.getParent().requestDisallowInterceptTouchEvent(disallow);
            if (current == binding.previewCanvas) {
                break;
            }
            if (!(current.getParent() instanceof View)) {
                break;
            }
            current = (View) current.getParent();
        }
        binding.previewCanvas.requestDisallowInterceptTouchEvent(disallow);
    }

    private void positionContainer(View container, UiComponent component) {
        FrameLayout.LayoutParams params = canvasItemLayoutParams(component);
        container.setLayoutParams(params);
    }

    private void showResizeHandles(View touchedView, int index) {
        View container = findCanvasContainer(touchedView);
        if (!(container instanceof FrameLayout)) {
            logDebug("Resize handles skipped; container missing index=" + index);
            return;
        }
        removeResizeHandles((FrameLayout) container);
        addResizeHandles((FrameLayout) container, index);
    }

    private void removeResizeHandles(FrameLayout container) {
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            View child = container.getChildAt(i);
            Object tag = child.getTag();
            if ("resize_handle".equals(tag)) {
                container.removeViewAt(i);
            }
        }
    }

    private void clearResizeHandlesFromCanvas() {
        for (int i = 0; i < binding.previewCanvas.getChildCount(); i++) {
            View child = binding.previewCanvas.getChildAt(i);
            if (child instanceof FrameLayout) {
                removeResizeHandles((FrameLayout) child);
            }
        }
        logDebug("Resize handles cleared");
    }

    private void addPaletteComponentAt(UiComponentType type, float dropX, float dropY) {
        UiComponent component = UiComponent.createDefault(type);
        component.setFullWidth(false);
        component.setWidthDp(defaultFreeWidthDp(component));
        component.setXdp(pxToDp(dropX) - 24);
        component.setYdp(pxToDp(dropY) - 24);
        clampComponentToCanvas(component);
        components.add(component);
        selectedIndex = components.size() - 1;
        resizeHandlesVisible = false;
        logDebug("Palette drop type=" + type + " canvasX=" + dropX + " canvasY=" + dropY
                + " index=" + selectedIndex + " xDp=" + component.getXdp()
                + " yDp=" + component.getYdp() + " widthDp=" + resolveComponentWidthDp(component)
                + " heightDp=" + resolveComponentHeightDp(component));
    }

    private void clampComponentToCanvas(UiComponent component) {
        int width = resolveComponentWidthDp(component);
        int height = resolveComponentHeightDp(component);
        int maxX = Math.max(0, canvasWidthDp() - width);
        int maxY = Math.max(0, canvasHeightDp() - height);
        component.setXdp(clamp(component.getXdp(), 0, maxX));
        component.setYdp(clamp(component.getYdp(), 0, maxY));
        if (component.getWidthDp() > 0) {
            component.setWidthDp(Math.min(component.getWidthDp(), canvasWidthDp()));
        }
        if (component.getHeightDp() > 0) {
            component.setHeightDp(clamp(component.getHeightDp(), minComponentHeightDp(component), canvasHeightDp()));
        }
    }

    private int resolveComponentWidthDp(UiComponent component) {
        int canvasWidth = canvasWidthDp();
        if (component.getWidthDp() > 0) {
            return clamp(component.getWidthDp(), MIN_WIDGET_WIDTH_DP, canvasWidth);
        }
        if (component.isFullWidth()) {
            return Math.max(MIN_WIDGET_WIDTH_DP, canvasWidth - component.getXdp());
        }
        return clamp(Math.round(canvasWidth * (component.getWidthPercent() / 100f)), MIN_WIDGET_WIDTH_DP, canvasWidth);
    }

    private int defaultFreeWidthDp(UiComponent component) {
        int canvasWidth = canvasWidthDp();
        switch (component.getType()) {
            case BUTTON:
            case DROPDOWN:
            case INPUT:
                return clamp(180, MIN_WIDGET_WIDTH_DP, canvasWidth);
            case CHECKBOX:
            case SWITCH:
            case DIVIDER:
            case PROGRESS:
                return clamp(190, MIN_WIDGET_WIDTH_DP, canvasWidth);
            default:
                return clamp(210, MIN_WIDGET_WIDTH_DP, canvasWidth);
        }
    }

    private int resolveComponentHeightDp(UiComponent component) {
        if (component.getHeightDp() > 0) {
            return clamp(component.getHeightDp(), minComponentHeightDp(component), canvasHeightDp());
        }
        switch (component.getType()) {
            case HEADER:
                return 120;
            case TEXT:
                return 92;
            case INPUT:
                return 74;
            case CARD:
                return 118;
            default:
                return 72;
        }
    }

    private int minComponentHeightDp(UiComponent component) {
        switch (component.getType()) {
            case HEADER:
                return 72;
            case TEXT:
                return 56;
            case BUTTON:
                return 48;
            case INPUT:
            case DROPDOWN:
                return 56;
            case CARD:
                return 72;
            case IMAGE:
                return 80;
            case TABS:
                return 44;
            case CHECKBOX:
            case SWITCH:
                return 48;
            case PROGRESS:
                return 56;
            case DIVIDER:
                return 18;
            default:
                return MIN_WIDGET_HEIGHT_DP;
        }
    }

    private int canvasWidthDp() {
        return binding.previewCanvas.getWidth() > 0 ? pxToDp(binding.previewCanvas.getWidth()) : CANVAS_WIDTH_DP;
    }

    private int canvasHeightDp() {
        return binding.previewCanvas.getHeight() > 0 ? pxToDp(binding.previewCanvas.getHeight()) : CANVAS_HEIGHT_DP;
    }

    private void assignDefaultPositions(List<UiComponent> items) {
        for (int i = 0; i < items.size(); i++) {
            UiComponent component = items.get(i);
            component.setXdp(12);
            component.setYdp(16 + (i * 96));
        }
    }

    private View createEmptyCanvasState() {
        TextView empty = new TextView(this);
        empty.setText(R.string.drag_here);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(ContextCompat.getColor(this, R.color.text_soft));
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        empty.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_stage_shell));
        empty.setPadding(dp(12), dp(32), dp(12), dp(32));
        return empty;
    }

    private boolean startDrag(View source, DragPayload payload) {
        ClipData data = ClipData.newPlainText("uiforge-drag", payload.describe());
        source.startDragAndDrop(data, new View.DragShadowBuilder(source), payload, 0);
        return true;
    }

    private boolean handlePreviewCanvasDrag(View view, DragEvent event) {
        Object state = event.getLocalState();
        if (!(state instanceof DragPayload)) {
            return false;
        }
        DragPayload payload = (DragPayload) state;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                dragInProgress = payload.isPaletteDrag();
                logDebug("Palette drag started accepted=" + dragInProgress);
                return dragInProgress;
            case DragEvent.ACTION_DRAG_LOCATION:
                return true;
            case DragEvent.ACTION_DROP:
                if (payload.isPaletteDrag()) {
                    addPaletteComponentAt(payload.type, canvasEventX(view, event), canvasEventY(view, event));
                    binding.previewCanvas.post(this::refreshAll);
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                dragInProgress = false;
                dragTargetIndex = -1;
                logDebug("Palette drag ended");
                return true;
            default:
                return true;
        }
    }

    private float canvasEventX(View eventView, DragEvent event) {
        if (eventView == binding.previewCanvas) {
            return event.getX();
        }
        int[] canvasLocation = new int[2];
        int[] viewLocation = new int[2];
        binding.previewCanvas.getLocationOnScreen(canvasLocation);
        eventView.getLocationOnScreen(viewLocation);
        return event.getX() + viewLocation[0] - canvasLocation[0];
    }

    private float canvasEventY(View eventView, DragEvent event) {
        if (eventView == binding.previewCanvas) {
            return event.getY();
        }
        int[] canvasLocation = new int[2];
        int[] viewLocation = new int[2];
        binding.previewCanvas.getLocationOnScreen(canvasLocation);
        eventView.getLocationOnScreen(viewLocation);
        return event.getY() + viewLocation[1] - canvasLocation[1];
    }

    private View createPreviewView(UiComponent component, boolean selected) {
        int background = colorFromName(component.getBackgroundColorName());
        int accent = colorFromName(component.getAccentColorName());
        int padding = dp(component.getPaddingDp());
        int radius = dp(component.getCornerRadiusDp());
        switch (component.getType()) {
            case HEADER:
                return createHeaderPreview(component, background, accent, padding, radius, selected);
            case TEXT:
                return createTextPreview(component, background, accent, padding, radius, selected);
            case BUTTON:
                return createButtonPreview(component, background, accent, padding, radius, selected);
            case INPUT:
                return createInputPreview(component, background, accent, padding, radius, selected);
            case CARD:
                return createCardPreview(component, background, accent, padding, radius, selected);
            case IMAGE:
                return createImagePreview(component, background, accent, padding, radius, selected);
            case TABS:
                return createTabsPreview(component, background, accent, padding, radius, selected);
            case DROPDOWN:
                return createDropdownPreview(component, background, accent, padding, radius, selected);
            case CHECKBOX:
                return createCheckboxPreview(component, background, accent, padding, radius, selected);
            case SWITCH:
                return createSwitchPreview(component, background, accent, padding, radius, selected);
            case DIVIDER:
                return createDividerPreview(component, background, accent, padding, radius, selected);
            case PROGRESS:
            default:
                return createProgressPreview(component, background, accent, padding, radius, selected);
        }
    }

    private View createHeaderPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        TextView title = new TextView(this);
        title.setText(component.getTitle());
        title.setTextColor(accent);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView helper = new TextView(this);
        helper.setText(component.getHelper());
        helper.setTextColor(adjustAlpha(accent, 0.72f));
        helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10, component.getTextSizeSp() - 10));
        helper.setPadding(0, dp(8), 0, 0);
        layout.addView(title);
        layout.addView(helper);
        return layout;
    }

    private View createTextPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        TextView title = new TextView(this);
        title.setText(component.getTitle());
        title.setTextColor(accent);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        title.setTypeface(component.isEmphasized() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        TextView helper = new TextView(this);
        helper.setText(component.getHelper());
        helper.setTextColor(adjustAlpha(accent, 0.72f));
        helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10, component.getTextSizeSp() - 4));
        helper.setPadding(0, dp(6), 0, 0);
        layout.addView(title);
        layout.addView(helper);
        return layout;
    }

    private View createButtonPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        MaterialButton button = new MaterialButton(this);
        button.setText(component.getTitle());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(padding, padding, padding, padding);
        button.setMinHeight(component.getHeightDp() == 0 ? dp(48) : dp(component.getHeightDp()));
        button.setCornerRadius(component.getCornerRadiusDp() * 2);
        if (component.isEmphasized()) {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
            button.setTextColor(accent);
        } else {
            button.setStrokeWidth(dp(1));
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(accent));
            button.setTextColor(accent);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        }
        if (selected) {
            button.setStrokeWidth(dp(2));
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_cobalt)));
        }
        return button;
    }

    private View createInputPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        TextInputLayout layout = new TextInputLayout(this);
        AppCompatEditText editText = new AppCompatEditText(this);
        editText.setText(component.getHelper());
        editText.setFocusable(false);
        editText.setTextColor(accent);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        editText.setPadding(padding, padding, padding, padding);
        layout.setHint(component.getTitle());
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxCornerRadii(radius, radius, radius, radius);
        layout.setBoxBackgroundColor(background);
        layout.setBoxStrokeColor(selected ? ContextCompat.getColor(this, R.color.accent_cobalt) : accent);
        layout.addView(editText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private View createCardPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        TextView eyebrow = new TextView(this);
        eyebrow.setText("CONTENT BLOCK");
        eyebrow.setAllCaps(true);
        eyebrow.setLetterSpacing(0.08f);
        eyebrow.setTextColor(adjustAlpha(accent, 0.72f));
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        TextView title = new TextView(this);
        title.setText(component.getTitle());
        title.setTextColor(accent);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        title.setPadding(0, dp(10), 0, 0);
        TextView helper = new TextView(this);
        helper.setText(component.getHelper());
        helper.setTextColor(adjustAlpha(accent, 0.72f));
        helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10, component.getTextSizeSp() - 5));
        helper.setPadding(0, dp(6), 0, 0);
        layout.addView(eyebrow);
        layout.addView(title);
        layout.addView(helper);
        return layout;
    }

    private View createImagePreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        ImageView image = new ImageView(this);
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        image.setColorFilter(accent);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        imageParams.gravity = Gravity.CENTER_HORIZONTAL;
        layout.addView(image, imageParams);
        Space spacer = new Space(this);
        layout.addView(spacer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
        TextView title = new TextView(this);
        title.setText(component.getTitle());
        title.setTextColor(accent);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView helper = new TextView(this);
        helper.setText(component.getHelper());
        helper.setTextColor(adjustAlpha(accent, 0.72f));
        helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10, component.getTextSizeSp() - 4));
        helper.setGravity(Gravity.CENTER_HORIZONTAL);
        helper.setPadding(0, dp(6), 0, 0);
        layout.addView(title);
        layout.addView(helper);
        return layout;
    }

    private View createTabsPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        String[] rawTabs = component.getTitle().split(",");
        String activeTab = component.getHelper();
        for (String rawTab : rawTabs) {
            String tab = rawTab.trim();
            if (tab.isEmpty()) {
                continue;
            }
            boolean active = tab.equalsIgnoreCase(activeTab == null ? "" : activeTab.trim());
            TextView label = new TextView(this);
            label.setText(tab);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
            label.setTypeface(active || component.isEmphasized() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            label.setTextColor(active ? background : accent);
            GradientDrawable tabBg = new GradientDrawable();
            tabBg.setCornerRadius(dp(Math.max(8, component.getCornerRadiusDp() - 4)));
            tabBg.setColor(active ? accent : Color.TRANSPARENT);
            label.setBackground(tabBg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(2), 0, dp(2), 0);
            layout.addView(label, params);
        }
        return layout;
    }

    private View createDropdownPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(nonEmpty(component.getHelper(), component.getTitle()));
        label.setTextColor(accent);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        TextView arrow = new TextView(this);
        arrow.setText("v");
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(accent);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        layout.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        layout.addView(arrow, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private View createCheckboxPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(component.getTitle());
        checkBox.setTextColor(accent);
        checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        checkBox.setChecked(isAffirmative(component.getHelper()));
        checkBox.setEnabled(false);
        layout.addView(checkBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private View createSwitchPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this);
        label.setText(component.getTitle());
        label.setTextColor(accent);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        SwitchMaterial switchView = new SwitchMaterial(this);
        switchView.setChecked(isAffirmative(component.getHelper()));
        switchView.setEnabled(false);
        layout.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        layout.addView(switchView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private View createDividerPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(accent);
        layout.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(component.isEmphasized() ? 3 : 1)));
        return layout;
    }

    private View createProgressPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, padding, selected);
        TextView label = new TextView(this);
        label.setText(component.getTitle());
        label.setTextColor(accent);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(parsePercent(component.getHelper(), 65));
        TextView value = new TextView(this);
        value.setText(component.getHelper());
        value.setGravity(Gravity.END);
        value.setTextColor(adjustAlpha(accent, 0.72f));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10, component.getTextSizeSp() - 2));
        layout.addView(label);
        layout.addView(progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)));
        layout.addView(value);
        return layout;
    }

    private LinearLayout shellLayout(int background, int radius, int padding, boolean selected) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(selected ? 2 : 1),
                selected ? ContextCompat.getColor(this, R.color.accent_cobalt)
                        : ContextCompat.getColor(this, R.color.stroke_soft));
        layout.setBackground(drawable);
        return layout;
    }

    private void updateSelected(ComponentMutation mutation) {
        if (bindingInspector) {
            return;
        }
        UiComponent selected = getSelected();
        if (selected == null) {
            return;
        }
        mutation.apply(selected);
        refreshAll();
    }

    private UiComponent getSelected() {
        if (selectedIndex < 0 || selectedIndex >= components.size()) {
            return null;
        }
        return components.get(selectedIndex);
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= components.size()) {
            return;
        }
        UiComponent removed = components.get(selectedIndex);
        logDebug("Delete component index=" + selectedIndex + " type=" + removed.getType());
        components.remove(selectedIndex);
        if (components.isEmpty()) {
            selectedIndex = -1;
        } else if (selectedIndex >= components.size()) {
            selectedIndex = components.size() - 1;
        }
        resizeHandlesVisible = false;
        refreshAll();
    }

    private void showExportDialog() {
        try {
            String payload = buildProjectJson().toString(2);
            logDebug("Export JSON opened components=" + components.size() + " bytes=" + payload.length());
            TextView exportText = dialogText(payload);
            exportText.setTypeface(Typeface.MONOSPACE);
            exportText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(exportText);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.export_title)
                    .setView(scrollView)
                    .setPositiveButton(R.string.copy_json, (dialog, which) -> copyToClipboard(payload))
                    .setNegativeButton(R.string.close_label, null)
                    .show();
        } catch (JSONException e) {
            logError("Export JSON failed", e);
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSaveProjectDialog() {
        TextInputEditText input = new TextInputEditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(nonEmpty(textOf(binding.projectNameInput), "Untitled Flow"));
        input.setSelectAllOnFocus(true);
        input.setSelection(input.getText() == null ? 0 : input.getText().length());

        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(getString(R.string.save_name_hint));
        layout.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout content = dialogColumn();
        content.addView(dialogText(getString(R.string.save_project_message)));
        content.addView(layout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_project_title)
                .setView(content)
                .setPositiveButton(R.string.save_label, (dialog, which) -> {
                    String requestedName = nonEmpty(textOf(input), "Untitled Flow");
                    saveProjectWithOverwriteCheck(requestedName);
                })
                .setNegativeButton(R.string.close_label, null)
                .show();
    }

    private void saveProjectWithOverwriteCheck(String requestedName) {
        File projectFile = getProjectFile(requestedName);
        logDebug("Save requested name=" + requestedName + " path=" + projectFile.getAbsolutePath()
                + " exists=" + projectFile.exists());
        if (projectFile.exists()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.overwrite_project_title)
                    .setMessage(R.string.overwrite_project_message)
                    .setPositiveButton(R.string.save_label, (dialog, which) -> saveProjectFile(projectFile, requestedName))
                    .setNegativeButton(R.string.close_label, null)
                    .show();
            return;
        }
        saveProjectFile(projectFile, requestedName);
    }

    private void saveProjectFile(File projectFile, String projectName) {
        try {
            File directory = projectFile.getParentFile();
            if (directory != null && !directory.exists() && !directory.mkdirs()) {
                throw new IOException("Could not create projects directory");
            }
            JSONObject root = buildProjectJson();
            root.put("projectName", projectName);
            try (FileOutputStream stream = new FileOutputStream(projectFile, false)) {
                stream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            binding.projectNameInput.setText(projectName);
            Toast.makeText(this, R.string.project_saved, Toast.LENGTH_SHORT).show();
            logDebug("Project saved name=" + projectName + " path=" + projectFile.getAbsolutePath()
                    + " components=" + components.size() + " bytes=" + projectFile.length());
        } catch (IOException | JSONException e) {
            logError("Project save failed path=" + projectFile.getAbsolutePath(), e);
            Toast.makeText(this, R.string.project_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoadProjectDialog() {
        File[] savedProjects = listSavedProjectFiles();
        logDebug("Load dialog opened savedCount=" + savedProjects.length);
        if (savedProjects.length == 0) {
            Toast.makeText(this, R.string.no_saved_projects, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout content = dialogColumn();
        for (int i = 0; i < savedProjects.length; i++) {
            File project = savedProjects[i];
            MaterialButton button = new MaterialButton(this);
            button.setText(displayNameFor(project));
            button.setTextColor(ContextCompat.getColor(this, R.color.text_strong));
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_selected)));
            button.setCornerRadius(dp(12));
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setOnClickListener(v -> {
                loadProjectFile(project);
                if (v.getRootView() != null) {
                    // The dialog closes through the button listener below.
                }
            });
            content.addView(button, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.load_project_title)
                .setView(content)
                .setNegativeButton(R.string.close_label, null)
                .create();
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            child.setOnClickListener(v -> {
                int index = content.indexOfChild(v);
                loadProjectFile(savedProjects[index]);
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private void showHelpDialog(int titleResId, int messageResId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleResId)
                .setView(dialogText(getString(messageResId)))
                .setPositiveButton(R.string.close_label, null)
                .show();
    }

    private void showHelpScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(dialogText(getString(R.string.help_screen_body)));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.help_screen_title)
                .setView(scrollView)
                .setPositiveButton(R.string.close_label, null)
                .show();
    }

    private void showAboutDialog() {
        LinearLayout content = dialogColumn();
        content.addView(dialogText(getString(R.string.about_creator)));
        content.addView(dialogText(getString(R.string.about_version, getAppVersionName())));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setView(content)
                .setPositiveButton(R.string.close_label, null)
                .show();
    }

    private String getAppVersionName() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return getPackageManager()
                        .getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0))
                        .versionName;
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            logError("Could not read app version", e);
            return "unknown";
        }
    }

    private LinearLayout dialogColumn() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(4);
        content.setPadding(padding, padding, padding, padding);
        return content;
    }

    private TextView dialogText(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(ContextCompat.getColor(this, R.color.text_strong));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        textView.setLineSpacing(dp(2), 1.0f);
        int padding = dp(12);
        textView.setPadding(padding, padding, padding, padding);
        return textView;
    }

    private void loadProjectFile(File projectFile) {
        logDebug("Project load start path=" + projectFile.getAbsolutePath() + " bytes=" + projectFile.length());
        try (FileInputStream stream = new FileInputStream(projectFile)) {
            byte[] bytes = new byte[(int) projectFile.length()];
            int read = stream.read(bytes);
            if (read <= 0) {
                throw new IOException("Empty file");
            }
            JSONObject root = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
            applyProjectJson(root);
            Toast.makeText(this, R.string.project_loaded, Toast.LENGTH_SHORT).show();
            logDebug("Project load success path=" + projectFile.getAbsolutePath()
                    + " components=" + components.size());
        } catch (IOException | JSONException e) {
            logError("Project load failed path=" + projectFile.getAbsolutePath(), e);
            Toast.makeText(this, R.string.project_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private JSONObject buildProjectJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("projectName", nonEmpty(textOf(binding.projectNameInput), "Untitled Flow"));
        JSONArray items = new JSONArray();
        for (UiComponent component : components) {
            JSONObject item = new JSONObject();
            item.put("type", component.getType().name());
            item.put("title", component.getTitle());
            item.put("helper", component.getHelper());
            item.put("background", component.getBackgroundColorName());
            item.put("accent", component.getAccentColorName());
            item.put("fullWidth", component.isFullWidth());
            item.put("emphasized", component.isEmphasized());
            item.put("paddingDp", component.getPaddingDp());
            item.put("cornerRadiusDp", component.getCornerRadiusDp());
            item.put("alignment", component.getAlignment());
            item.put("widthPercent", component.getWidthPercent());
            item.put("widthDp", component.getWidthDp());
            item.put("heightDp", component.getHeightDp());
            item.put("textSizeSp", component.getTextSizeSp());
            item.put("xDp", component.getXdp());
            item.put("yDp", component.getYdp());
            items.put(item);
        }
        root.put("components", items);
        return root;
    }

    private void applyProjectJson(JSONObject root) throws JSONException {
        components.clear();
        JSONArray items = root.optJSONArray("components");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                UiComponentType type = UiComponentType.valueOf(item.getString("type"));
                UiComponent component = new UiComponent(
                        type,
                        item.optString("title"),
                        item.optString("helper"),
                        item.optString("background", "White"),
                        item.optString("accent", "Ink"),
                        item.optBoolean("fullWidth", true),
                        item.optBoolean("emphasized", false),
                        item.optInt("paddingDp", 16),
                        item.optInt("cornerRadiusDp", 18),
                        item.optString("alignment", "start"));
                component.setWidthPercent(item.optInt("widthPercent", 100));
                component.setWidthDp(item.optInt("widthDp", 0));
                component.setHeightDp(item.optInt("heightDp", component.getHeightDp()));
                component.setTextSizeSp(item.optInt("textSizeSp", component.getTextSizeSp()));
                if (item.has("xDp") || item.has("yDp")) {
                    component.setXdp(item.optInt("xDp", 12));
                    component.setYdp(item.optInt("yDp", 16));
                } else {
                    component.setXdp(12);
                    component.setYdp(16 + (i * 96));
                }
                components.add(component);
            }
        }
        binding.projectNameInput.setText(root.optString("projectName", "Untitled Flow"));
        selectedIndex = components.isEmpty() ? -1 : 0;
        resizeHandlesVisible = false;
        logDebug("Applied project JSON name=" + root.optString("projectName", "Untitled Flow")
                + " components=" + components.size());
        refreshAll();
    }

    private File getProjectsDirectory() {
        return new File(getFilesDir(), PROJECTS_DIR);
    }

    private File getProjectFile(String projectName) {
        return new File(getProjectsDirectory(), slugify(projectName) + PROJECT_EXTENSION);
    }

    private File[] listSavedProjectFiles() {
        File directory = getProjectsDirectory();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(PROJECT_EXTENSION));
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    private String displayNameFor(File projectFile) {
        String savedName = readSavedProjectName(projectFile);
        if (!savedName.isEmpty()) {
            return savedName;
        }
        String fileName = projectFile.getName();
        if (fileName.endsWith(PROJECT_EXTENSION)) {
            return fileName.substring(0, fileName.length() - PROJECT_EXTENSION.length()).replace('-', ' ');
        }
        return fileName;
    }

    private String readSavedProjectName(File projectFile) {
        try (FileInputStream stream = new FileInputStream(projectFile)) {
            byte[] bytes = new byte[(int) projectFile.length()];
            int read = stream.read(bytes);
            if (read <= 0) {
                return "";
            }
            JSONObject root = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
            return nonEmpty(root.optString("projectName"), "");
        } catch (IOException | JSONException e) {
            return "";
        }
    }

    private String slugify(String projectName) {
        String normalized = projectName.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("(^-+|-+$)", "");
        return normalized.isEmpty() ? "untitled-flow" : normalized;
    }

    private void copyToClipboard(String payload) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("ui-spec", payload));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreState(Bundle savedInstanceState) {
        binding.projectNameInput.setText(savedInstanceState.getString(STATE_PROJECT_NAME, ""));
        ArrayList<UiComponent> restored = (ArrayList<UiComponent>) savedInstanceState.getSerializable(STATE_COMPONENTS);
        components.clear();
        if (restored != null) {
            components.addAll(restored);
        }
        selectedIndex = savedInstanceState.getInt(STATE_SELECTION, components.isEmpty() ? -1 : 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PROJECT_NAME, textOf(binding.projectNameInput));
        outState.putSerializable(STATE_COMPONENTS, new ArrayList<>(components));
        outState.putInt(STATE_SELECTION, selectedIndex);
    }

    @Override
    public void onSelect(int position) {
        selectedIndex = position;
        resizeHandlesVisible = false;
        logDebug("Layer selected index=" + position);
        refreshAll();
    }

    @Override
    public void onDelete(int position) {
        selectedIndex = position;
        logDebug("Layer delete requested index=" + position);
        deleteSelected();
    }

    @ColorInt
    private int colorFromName(String name) {
        Integer color = palette.get(name);
        return color != null ? color : Color.WHITE;
    }

    private int resolveGravity(String alignment) {
        if ("center".equals(alignment)) {
            return Gravity.CENTER_HORIZONTAL;
        }
        if ("end".equals(alignment)) {
            return Gravity.END;
        }
        return Gravity.START;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()));
    }

    private int pxToDp(float value) {
        return Math.round(value / getResources().getDisplayMetrics().density);
    }

    private void logDebug(String message) {
        Log.d(LOG_TAG, message);
        if (shouldWriteDebugToFile(message)) {
            writeFileLog("D", message, null);
        }
    }

    private void logError(String message, Throwable throwable) {
        Log.e(LOG_TAG, message, throwable);
        writeFileLog("E", message, throwable);
    }

    private void installCrashLogger() {
        if (previousUncaughtExceptionHandler != null) {
            return;
        }
        previousUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String line = buildFileLogLine("E", "Uncaught exception thread=" + thread.getName(), throwable);
            try {
                appendToDownloadsLog(line);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Could not write uncaught exception to Downloads/" + FILE_LOG_NAME, e);
            }
            if (previousUncaughtExceptionHandler != null) {
                previousUncaughtExceptionHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private boolean shouldWriteDebugToFile(String message) {
        if (message.startsWith("Move ") || message.startsWith("Resize move ")) {
            long now = System.currentTimeMillis();
            if (now - lastMoveFileLogAtMs < 500L) {
                return false;
            }
            lastMoveFileLogAtMs = now;
        }
        return true;
    }

    private void writeFileLog(String level, String message, Throwable throwable) {
        String line = buildFileLogLine(level, message, throwable);
        fileLogExecutor.execute(() -> {
            try {
                appendToDownloadsLog(line);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Could not write Downloads/" + FILE_LOG_NAME, e);
            }
        });
    }

    private String buildFileLogLine(String level, String message, Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        builder.append(fileLogDateFormat.format(new Date()))
                .append(' ')
                .append(level)
                .append('/')
                .append(LOG_TAG)
                .append(": ")
                .append(message)
                .append('\n');
        if (throwable != null) {
            StringWriter stringWriter = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stringWriter));
            builder.append(stringWriter).append('\n');
        }
        return builder.toString();
    }

    private void appendToDownloadsLog(String line) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri logUri = getDownloadsLogUri();
            try (OutputStream stream = getContentResolver().openOutputStream(logUri, "wa")) {
                if (stream == null) {
                    throw new IOException("Could not open MediaStore output stream");
                }
                stream.write(line.getBytes(StandardCharsets.UTF_8));
            }
            return;
        }
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloads.exists() && !downloads.mkdirs()) {
            throw new IOException("Could not create Downloads directory");
        }
        try (FileOutputStream stream = new FileOutputStream(new File(downloads, FILE_LOG_NAME), true)) {
            stream.write(line.getBytes(StandardCharsets.UTF_8));
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri getDownloadsLogUri() throws IOException {
        if (cachedDownloadsLogUri != null && canAppendToUri(cachedDownloadsLogUri)) {
            return cachedDownloadsLogUri;
        }
        SharedPreferences preferences = getSharedPreferences(FILE_LOG_PREFS, Context.MODE_PRIVATE);
        String savedUri = preferences.getString(FILE_LOG_URI_PREF, "");
        if (savedUri != null && !savedUri.isEmpty()) {
            Uri parsed = Uri.parse(savedUri);
            if (canAppendToUri(parsed)) {
                cachedDownloadsLogUri = parsed;
                return parsed;
            }
            preferences.edit().remove(FILE_LOG_URI_PREF).apply();
        }
        Uri found = findExistingDownloadsLogUri();
        if (found != null) {
            cachedDownloadsLogUri = found;
            preferences.edit().putString(FILE_LOG_URI_PREF, found.toString()).apply();
            return found;
        }
        Uri created = createDownloadsLogUri();
        cachedDownloadsLogUri = created;
        preferences.edit().putString(FILE_LOG_URI_PREF, created.toString()).apply();
        return created;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri findExistingDownloadsLogUri() {
        ContentResolver resolver = getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = new String[]{MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND ("
                + MediaStore.Downloads.RELATIVE_PATH + "=? OR "
                + MediaStore.Downloads.RELATIVE_PATH + "=?)";
        String[] selectionArgs = new String[]{
                FILE_LOG_NAME,
                Environment.DIRECTORY_DOWNLOADS + "/",
                Environment.DIRECTORY_DOWNLOADS
        };
        String sortOrder = MediaStore.Downloads.DATE_MODIFIED + " DESC";
        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(collection, id);
            }
        }
        return null;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private Uri createDownloadsLogUri() throws IOException {
        ContentResolver resolver = getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_LOG_NAME);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        Uri created = resolver.insert(collection, values);
        if (created == null) {
            throw new IOException("Could not create Downloads log file");
        }
        return created;
    }

    private boolean canAppendToUri(Uri uri) {
        try (OutputStream stream = getContentResolver().openOutputStream(uri, "wa")) {
            return stream != null;
        } catch (IOException | SecurityException e) {
            Log.w(LOG_TAG, "Saved Downloads log URI is not writable: " + uri, e);
            return false;
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private String textOf(TextInputEditText editText) {
        Editable editable = editText.getText();
        return editable == null ? "" : editable.toString();
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private boolean isAffirmative(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("checked");
    }

    private int parsePercent(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return clamp(Integer.parseInt(value.replace("%", "").trim()), 0, 100);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private TextWatcher simpleWatcher(TextConsumer consumer) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!bindingInspector) {
                    consumer.accept(s == null ? "" : s.toString());
                }
            }
        };
    }

    private interface TextConsumer {
        void accept(String value);
    }

    private interface ComponentMutation {
        void apply(UiComponent component);
    }

    private static final class DragPayload {
        private final UiComponentType type;
        private final int sourceIndex;

        private DragPayload(UiComponentType type, int sourceIndex) {
            this.type = type;
            this.sourceIndex = sourceIndex;
        }

        private boolean isPaletteDrag() {
            return type != null;
        }

        private String describe() {
            return isPaletteDrag() ? type.name() : "MOVE_" + sourceIndex;
        }
    }

    private abstract static class SimpleSeekBarListener implements android.widget.SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
        }
    }
}
