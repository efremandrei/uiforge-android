package com.uiforge.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
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
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
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
    private static final String SETTINGS_PREFS = "uiforge_settings";
    private static final String DARK_SKIN_PREF = "dark_skin";
    private static final String STATE_PROJECT_NAME = "project_name";
    private static final String STATE_COMPONENTS = "components";
    private static final String STATE_SELECTION = "selection";
    private static final String PROJECTS_DIR = "projects";
    private static final String PROJECT_EXTENSION = ".uiforge.json";
    private static final String HEX_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";
    private static final int CANVAS_WIDTH_DP = 320;
    private static final int CANVAS_HEIGHT_DP = 640;
    private static final int MIN_WIDGET_WIDTH_DP = 48;
    private static final int MIN_WIDGET_HEIGHT_DP = 24;

    private ActivityMainBinding binding;
    private final List<UiComponent> components = new ArrayList<>();
    private final Map<String, Integer> palette = new LinkedHashMap<>();
    private final ExecutorService fileLogExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat fileLogDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private LayerAdapter layerAdapter;
    private PopupWindow inspectorPopup;
    private final Map<String, Boolean> paletteGroupsExpanded = new LinkedHashMap<>();
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
        applySavedSkinMode();
        configureSystemBars();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupSystemBarInsets();
        setupInspectorDrawerHost();

        seedPalette();
        setupToolbar();
        setupLists();
        setupHelpButtons();
        styleProjectNameField();
        styleInspectorDrawer();
        setupDarkSkinSwitch();
        setupPaletteButtons();
        setupTemplateButtons();
        styleTemplateDropdown();
        setupInspector();
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
        binding.toolbar.setTitle(null);
        binding.toolbar.setSubtitle(null);
        binding.bottomHelpButton.setOnClickListener(v -> showHelpScreen());
        binding.bottomAboutButton.setOnClickListener(v -> showAboutDialog());
        binding.saveProjectButton.setOnClickListener(v -> showSaveProjectDialog());
        binding.loadProjectButton.setOnClickListener(v -> showLoadProjectDialog());
        binding.exportButton.setOnClickListener(v -> showExportDialog());
    }

    private void setupDarkSkinSwitch() {
        boolean darkSkin = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(DARK_SKIN_PREF, false);
        binding.darkSkinSwitch.setChecked(darkSkin);
        binding.darkSkinSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(DARK_SKIN_PREF, isChecked)
                    .apply();
            logDebug("Dark skin changed enabled=" + isChecked);
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });
    }

    private void applySavedSkinMode() {
        boolean darkSkin = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(DARK_SKIN_PREF, false);
        AppCompatDelegate.setDefaultNightMode(darkSkin
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void setupInspectorDrawerHost() {
        ViewGroup parent = (ViewGroup) binding.inspectorDrawer.getParent();
        if (parent != null) {
            parent.removeView(binding.inspectorDrawer);
        }
        inspectorPopup = new PopupWindow(
                binding.inspectorDrawer,
                inspectorDrawerWidth(),
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        inspectorPopup.setOutsideTouchable(true);
        inspectorPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        inspectorPopup.setElevation(dp(12));
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
        binding.closeInspectorDrawerButton.setOnClickListener(v -> closeInspectorDrawer());
    }

    private void configureHelpButton(MaterialButton button, boolean onDarkBackground) {
        int color = ContextCompat.getColor(this, onDarkBackground ? R.color.hero_text : R.color.text_strong);
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

    private void styleProjectNameField() {
        int input = ContextCompat.getColor(this, R.color.hero_input);
        int text = ContextCompat.getColor(this, R.color.hero_text);
        int hint = ContextCompat.getColor(this, R.color.hero_text_soft);
        ColorStateList hintList = ColorStateList.valueOf(hint);
        binding.projectNameLayout.setBoxBackgroundColor(input);
        binding.projectNameLayout.setBoxStrokeColor(hint);
        binding.projectNameLayout.setDefaultHintTextColor(hintList);
        binding.projectNameLayout.setHintTextColor(hintList);
        binding.projectNameInput.setTextColor(text);
        binding.projectNameInput.setHintTextColor(hint);
    }

    private void styleInspectorDrawer() {
        int surface = ContextCompat.getColor(this, R.color.surface_primary);
        int strong = ContextCompat.getColor(this, R.color.text_strong);
        int soft = ContextCompat.getColor(this, R.color.text_soft);
        int stroke = ContextCompat.getColor(this, R.color.stroke_soft);
        int accent = ContextCompat.getColor(this, R.color.accent_cobalt);
        int danger = ContextCompat.getColor(this, R.color.accent_sunset);
        ColorStateList strongText = ColorStateList.valueOf(strong);
        ColorStateList softText = ColorStateList.valueOf(soft);
        ColorStateList fieldStroke = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}},
                new int[]{accent, stroke, stroke});

        binding.inspectorDrawer.setCardBackgroundColor(surface);
        binding.inspectorDrawer.setStrokeColor(stroke);
        styleInspectorViewTree(binding.inspectorDrawer, strongText, softText, fieldStroke, surface);

        binding.selectedTypeLabel.setTextColor(softText);
        binding.widthValueLabel.setTextColor(softText);
        binding.heightValueLabel.setTextColor(softText);
        binding.textSizeValueLabel.setTextColor(softText);
        binding.paddingValueLabel.setTextColor(softText);
        binding.radiusValueLabel.setTextColor(softText);
        binding.opacityValueLabel.setTextColor(softText);
        binding.deleteButton.setTextColor(ColorStateList.valueOf(danger));
        binding.deleteButton.setStrokeColor(ColorStateList.valueOf(danger));
    }

    private void styleInspectorViewTree(
            View view,
            ColorStateList strongText,
            ColorStateList softText,
            ColorStateList fieldStroke,
            int surface) {
        if (view instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) view;
            layout.setBoxBackgroundColor(surface);
            layout.setBoxStrokeColorStateList(fieldStroke);
            layout.setDefaultHintTextColor(softText);
            layout.setHintTextColor(softText);
            layout.setEndIconTintList(fieldStroke);
        }
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(strongText);
            ((TextView) view).setHintTextColor(softText);
        }
        if (view instanceof MaterialButton) {
            ((MaterialButton) view).setIconTint(strongText);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleInspectorViewTree(group.getChildAt(i), strongText, softText, fieldStroke, surface);
            }
        }
    }

    private void setupLists() {
        binding.layersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        layerAdapter = new LayerAdapter(components, this);
        binding.layersRecyclerView.setAdapter(layerAdapter);
    }

    private void setupPaletteButtons() {
        for (String groupName : paletteGroups().keySet()) {
            if (!paletteGroupsExpanded.containsKey(groupName)) {
                paletteGroupsExpanded.put(groupName, "Basics".equals(groupName));
            }
        }
        renderPaletteGroups();
    }

    private Map<String, List<UiComponentType>> paletteGroups() {
        Map<String, List<UiComponentType>> groups = new LinkedHashMap<>();
        groups.put("Basics", Arrays.asList(
                UiComponentType.HEADER,
                UiComponentType.TEXT,
                UiComponentType.BUTTON,
                UiComponentType.INPUT,
                UiComponentType.TEXT_AREA,
                UiComponentType.SEARCH_BAR,
                UiComponentType.CARD,
                UiComponentType.IMAGE));
        groups.put("Input & Choice", Arrays.asList(
                UiComponentType.TABS,
                UiComponentType.DROPDOWN,
                UiComponentType.CHECKBOX,
                UiComponentType.RADIO_GROUP,
                UiComponentType.SWITCH,
                UiComponentType.SLIDER));
        groups.put("Navigation", Arrays.asList(
                UiComponentType.TOP_APP_BAR,
                UiComponentType.BOTTOM_NAV,
                UiComponentType.FAB,
                UiComponentType.ICON_BUTTON,
                UiComponentType.MENU));
        groups.put("Content & Feedback", Arrays.asList(
                UiComponentType.AVATAR,
                UiComponentType.BADGE,
                UiComponentType.CHIP,
                UiComponentType.LIST_ITEM,
                UiComponentType.GRID_ITEM,
                UiComponentType.DIALOG,
                UiComponentType.SNACKBAR,
                UiComponentType.RATING,
                UiComponentType.STEPPER,
                UiComponentType.PROGRESS,
                UiComponentType.LOADING,
                UiComponentType.SKELETON));
        groups.put("Media & Data", Arrays.asList(
                UiComponentType.MAP,
                UiComponentType.CHART,
                UiComponentType.MEDIA_PLAYER,
                UiComponentType.QR_CODE));
        groups.put("Layout", Arrays.asList(
                UiComponentType.DIVIDER,
                UiComponentType.SPACER,
                UiComponentType.ROW,
                UiComponentType.COLUMN,
                UiComponentType.GRID));
        return groups;
    }

    private void renderPaletteGroups() {
        binding.paletteGroupsContainer.removeAllViews();
        int accent = ContextCompat.getColor(this, R.color.accent_cobalt);
        int surface = ContextCompat.getColor(this, R.color.surface_primary);
        int selectedSurface = ContextCompat.getColor(this, R.color.surface_selected);
        int strong = ContextCompat.getColor(this, R.color.text_strong);
        for (Map.Entry<String, List<UiComponentType>> entry : paletteGroups().entrySet()) {
            String groupName = entry.getKey();
            boolean expanded = Boolean.TRUE.equals(paletteGroupsExpanded.get(groupName));

            MaterialButton header = new MaterialButton(this);
            header.setText((expanded ? "- " : "+ ") + groupName);
            header.setAllCaps(false);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setTextColor(strong);
            header.setBackgroundTintList(ColorStateList.valueOf(selectedSurface));
            header.setStrokeColor(ColorStateList.valueOf(accent));
            header.setStrokeWidth(dp(1));
            header.setCornerRadius(dp(14));
            header.setMinHeight(dp(44));
            header.setInsetTop(0);
            header.setInsetBottom(0);
            header.setOnClickListener(v -> {
                paletteGroupsExpanded.put(groupName, !Boolean.TRUE.equals(paletteGroupsExpanded.get(groupName)));
                renderPaletteGroups();
            });
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(44));
            headerParams.setMargins(0, 0, 0, dp(8));
            binding.paletteGroupsContainer.addView(header, headerParams);

            if (!expanded) {
                continue;
            }

            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setFadingEdgeLength(dp(28));
            scroll.setHorizontalFadingEdgeEnabled(true);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, dp(8));
            for (UiComponentType type : entry.getValue()) {
                MaterialButton button = new MaterialButton(this);
                button.setText(type.getLabel());
                configurePaletteButton(button, type, surface, accent);
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(48));
                buttonParams.setMargins(0, 0, dp(8), 0);
                row.addView(button, buttonParams);
            }
            scroll.addView(row, new HorizontalScrollView.LayoutParams(
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            scrollParams.setMargins(0, 0, 0, dp(10));
            binding.paletteGroupsContainer.addView(scroll, scrollParams);
        }
    }

    private void configurePaletteButton(MaterialButton button, UiComponentType type, int surface, int accent) {
        button.setMinWidth(dp(92));
        button.setMinHeight(dp(48));
        button.setSingleLine(true);
        button.setTextColor(accent);
        button.setStrokeWidth(dp(2));
        button.setStrokeColor(ColorStateList.valueOf(accent));
        button.setBackgroundTintList(ColorStateList.valueOf(surface));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setOnClickListener(v -> Toast.makeText(this, R.string.drag_palette_toast, Toast.LENGTH_SHORT).show());
        button.setOnLongClickListener(v -> startDrag(v, new DragPayload(type, -1)));
    }

    private void setupTemplateButtons() {
        List<String> presets = Arrays.asList(
                getString(R.string.template_commerce),
                getString(R.string.template_profile),
                getString(R.string.template_onboarding),
                getString(R.string.template_banking),
                getString(R.string.template_food_delivery),
                getString(R.string.template_fitness));
        binding.presetDropdownInput.setAdapter(new ArrayAdapter<>(
                this,
                R.layout.item_dropdown,
                presets));
        binding.presetDropdownInput.setDropDownBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.bg_dropdown_popup));
        binding.presetDropdownInput.setOnItemClickListener((parent, view, position, id) ->
                applyPreset((String) parent.getItemAtPosition(position)));
        binding.startNewDesignButton.setOnClickListener(v -> startNewDesign());
    }

    private void styleTemplateDropdown() {
        int surface = ContextCompat.getColor(this, R.color.surface_primary);
        int strong = ContextCompat.getColor(this, R.color.text_strong);
        int soft = ContextCompat.getColor(this, R.color.text_soft);
        int accent = ContextCompat.getColor(this, R.color.accent_cobalt);
        ColorStateList accentOnly = ColorStateList.valueOf(accent);
        binding.presetDropdownLayout.setBoxBackgroundColor(surface);
        binding.presetDropdownLayout.setBoxStrokeColor(accent);
        binding.presetDropdownLayout.setEndIconTintList(accentOnly);
        binding.presetDropdownInput.setTextColor(strong);
        binding.presetDropdownInput.setHintTextColor(soft);
        binding.presetDropdownInput.setDropDownBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.bg_dropdown_popup));
    }

    private void applyPreset(String presetName) {
        if (getString(R.string.template_profile).equals(presetName)) {
            applyTemplate(createProfileTemplate(), "Profile Setup");
        } else if (getString(R.string.template_onboarding).equals(presetName)) {
            applyTemplate(createOnboardingTemplate(), "Onboarding Flow");
        } else if (getString(R.string.template_banking).equals(presetName)) {
            applyTemplate(createBankingTemplate(), "Mobile Banking");
        } else if (getString(R.string.template_food_delivery).equals(presetName)) {
            applyTemplate(createFoodDeliveryTemplate(), "Food Delivery");
        } else if (getString(R.string.template_fitness).equals(presetName)) {
            applyTemplate(createFitnessTemplate(), "Fitness Tracker");
        } else {
            applyTemplate(createCommerceTemplate(), "Commerce Checkout");
        }
        binding.presetDropdownInput.setText(presetName, false);
    }

    private void startNewDesign() {
        logDebug("Starting blank design");
        components.clear();
        binding.projectNameInput.setText("Untitled Flow");
        binding.presetDropdownInput.setText("", false);
        selectedIndex = -1;
        resizeHandlesVisible = false;
        refreshAll();
    }

    private void setupPreviewCanvas() {
        binding.previewCanvas.setOnDragListener(this::handlePreviewCanvasDrag);
    }

    private void wireDropdowns() {
        refreshColorDropdownAdapters();
        binding.backgroundColorInput.setDropDownBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.bg_dropdown_popup));
        binding.accentColorInput.setDropDownBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.bg_dropdown_popup));
        binding.backgroundColorInput.setOnItemClickListener((parent, view, position, id) ->
                handleColorSelection(true, (String) parent.getItemAtPosition(position)));
        binding.accentColorInput.setOnItemClickListener((parent, view, position, id) ->
                handleColorSelection(false, (String) parent.getItemAtPosition(position)));
    }

    private void refreshColorDropdownAdapters() {
        List<String> options = colorDropdownOptions();
        binding.backgroundColorInput.setAdapter(new ColorDropdownAdapter(options));
        binding.accentColorInput.setAdapter(new ColorDropdownAdapter(options));
    }

    private List<String> colorDropdownOptions() {
        List<String> options = new ArrayList<>(palette.keySet());
        for (UiComponent component : components) {
            addCustomColorOption(options, component.getBackgroundColorName());
            addCustomColorOption(options, component.getAccentColorName());
        }
        options.add(getString(R.string.custom_color_option));
        return options;
    }

    private void addCustomColorOption(List<String> options, String colorName) {
        if (!isHexColorName(colorName)) {
            return;
        }
        String normalized = normalizeHexColorName(colorFromName(colorName));
        if (!options.contains(normalized)) {
            options.add(normalized);
        }
    }

    private void handleColorSelection(boolean backgroundTarget, String colorName) {
        UiComponent selected = getSelected();
        if (selected == null) {
            return;
        }
        if (getString(R.string.custom_color_option).equals(colorName)) {
            MaterialAutoCompleteTextView input = backgroundTarget ? binding.backgroundColorInput : binding.accentColorInput;
            setColorInputValue(input, backgroundTarget ? selected.getBackgroundColorName() : selected.getAccentColorName());
            showCustomColorDialog(backgroundTarget);
            return;
        }
        updateSelected(component -> {
            if (backgroundTarget) {
                component.setBackgroundColorName(colorName);
            } else {
                component.setAccentColorName(colorName);
            }
        });
    }

    private void showCustomColorDialog(boolean backgroundTarget) {
        UiComponent selected = getSelected();
        if (selected == null) {
            return;
        }
        int initialColor = colorFromName(backgroundTarget
                ? selected.getBackgroundColorName()
                : selected.getAccentColorName());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(8), dp(4), 0);

        View preview = new View(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        previewParams.setMargins(0, 0, 0, dp(12));
        content.addView(preview, previewParams);

        TextInputLayout hexLayout = new TextInputLayout(this);
        hexLayout.setHint(getString(R.string.hex_color_hint));
        hexLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        hexLayout.setBoxBackgroundColor(ContextCompat.getColor(this, R.color.surface_primary));
        hexLayout.setBoxStrokeColor(ContextCompat.getColor(this, R.color.accent_cobalt));
        TextInputEditText hexInput = new TextInputEditText(this);
        hexInput.setSingleLine(true);
        hexInput.setTextColor(ContextCompat.getColor(this, R.color.text_strong));
        hexInput.setText(normalizeHexColorName(initialColor));
        hexInput.setSelection(hexInput.length());
        hexLayout.addView(hexInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(hexLayout);

        TextView redLabel = createColorSliderLabel(R.string.red_value, Color.red(initialColor));
        android.widget.SeekBar redSeek = createColorSeekBar(Color.red(initialColor));
        TextView greenLabel = createColorSliderLabel(R.string.green_value, Color.green(initialColor));
        android.widget.SeekBar greenSeek = createColorSeekBar(Color.green(initialColor));
        TextView blueLabel = createColorSliderLabel(R.string.blue_value, Color.blue(initialColor));
        android.widget.SeekBar blueSeek = createColorSeekBar(Color.blue(initialColor));
        addColorSlider(content, redLabel, redSeek);
        addColorSlider(content, greenLabel, greenSeek);
        addColorSlider(content, blueLabel, blueSeek);

        final boolean[] syncing = {false};
        Runnable updatePreviewFromSliders = () -> {
            int color = Color.rgb(redSeek.getProgress(), greenSeek.getProgress(), blueSeek.getProgress());
            preview.setBackground(colorSwatchBackground(color));
            redLabel.setText(getString(R.string.red_value) + ": " + redSeek.getProgress());
            greenLabel.setText(getString(R.string.green_value) + ": " + greenSeek.getProgress());
            blueLabel.setText(getString(R.string.blue_value) + ": " + blueSeek.getProgress());
            if (!syncing[0]) {
                syncing[0] = true;
                hexInput.setText(normalizeHexColorName(color));
                hexInput.setSelection(hexInput.length());
                syncing[0] = false;
            }
        };
        updatePreviewFromSliders.run();

        android.widget.SeekBar.OnSeekBarChangeListener colorSeekListener = new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                updatePreviewFromSliders.run();
            }
        };
        redSeek.setOnSeekBarChangeListener(colorSeekListener);
        greenSeek.setOnSeekBarChangeListener(colorSeekListener);
        blueSeek.setOnSeekBarChangeListener(colorSeekListener);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing[0]) {
                    return;
                }
                String value = s == null ? "" : s.toString().trim();
                if (!isHexColorName(value)) {
                    return;
                }
                int color = colorFromName(value);
                syncing[0] = true;
                redSeek.setProgress(Color.red(color));
                greenSeek.setProgress(Color.green(color));
                blueSeek.setProgress(Color.blue(color));
                syncing[0] = false;
                updatePreviewFromSliders.run();
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(backgroundTarget ? R.string.custom_background_color_title : R.string.custom_accent_color_title)
                .setView(content)
                .setPositiveButton(R.string.apply_label, null)
                .setNegativeButton(R.string.close_label, (d, which) -> bindInspector())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = textOf(hexInput).trim();
            if (!isHexColorName(value)) {
                Toast.makeText(this, R.string.invalid_color, Toast.LENGTH_SHORT).show();
                return;
            }
            String colorName = normalizeHexColorName(colorFromName(value));
            updateSelected(component -> {
                if (backgroundTarget) {
                    component.setBackgroundColorName(colorName);
                } else {
                    component.setAccentColorName(colorName);
                }
            });
            dialog.dismiss();
        }));
        dialog.show();
    }

    private TextView createColorSliderLabel(int labelRes, int value) {
        TextView label = new TextView(this);
        label.setText(getString(labelRes) + ": " + value);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_strong));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setPadding(0, dp(10), 0, 0);
        return label;
    }

    private android.widget.SeekBar createColorSeekBar(int value) {
        android.widget.SeekBar seekBar = new android.widget.SeekBar(this);
        seekBar.setMax(255);
        seekBar.setProgress(value);
        return seekBar;
    }

    private void addColorSlider(LinearLayout content, TextView label, android.widget.SeekBar seekBar) {
        content.addView(label);
        content.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
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
        binding.opacitySeekBar.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                binding.opacityValueLabel.setText(getString(R.string.percent_value, progress));
                if (fromUser) {
                    updateSelected(component -> component.setOpacityPercent(progress));
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

    private List<UiComponent> createBankingTemplate() {
        List<UiComponent> template = new ArrayList<>();
        template.add(new UiComponent(UiComponentType.HEADER, "Good morning, Andrei", "Checking balance and quick actions", "Cobalt", "White", true, true, 22, 28, "start"));
        template.add(new UiComponent(UiComponentType.CARD, "$12,480.42", "Available balance", "White", "Mint", true, true, 18, 24, "start"));
        template.add(new UiComponent(UiComponentType.TABS, "Accounts, Cards, Activity", "Accounts", "Ice", "Cobalt", true, true, 8, 18, "center"));
        template.add(new UiComponent(UiComponentType.BUTTON, "Transfer money", "", "Mint", "White", false, true, 16, 20, "center"));
        template.add(new UiComponent(UiComponentType.CARD, "Recent payment", "Coffee House - $8.20", "White", "Ink", true, false, 14, 18, "start"));
        return template;
    }

    private List<UiComponent> createFoodDeliveryTemplate() {
        List<UiComponent> template = new ArrayList<>();
        template.add(new UiComponent(UiComponentType.HEADER, "Dinner near you", "Fast delivery from top rated kitchens", "Sunset", "White", true, true, 22, 28, "start"));
        template.add(new UiComponent(UiComponentType.INPUT, "Search restaurants", "Pizza, sushi, burgers", "White", "Ink", true, false, 14, 18, "start"));
        template.add(new UiComponent(UiComponentType.DROPDOWN, "Delivery time", "ASAP", "Ice", "Cobalt", false, false, 14, 18, "start"));
        template.add(new UiComponent(UiComponentType.CARD, "Green Bowl", "25-35 min - 4.8 rating", "White", "Mint", true, false, 18, 24, "start"));
        template.add(new UiComponent(UiComponentType.BUTTON, "View menu", "", "Sunset", "White", false, true, 16, 20, "center"));
        return template;
    }

    private List<UiComponent> createFitnessTemplate() {
        List<UiComponent> template = new ArrayList<>();
        template.add(new UiComponent(UiComponentType.HEADER, "Today workout", "Strength plan and activity summary", "Charcoal", "Gold", true, true, 22, 28, "start"));
        template.add(new UiComponent(UiComponentType.PROGRESS, "Daily movement", "68%", "White", "Mint", true, false, 14, 18, "start"));
        template.add(new UiComponent(UiComponentType.CHECKBOX, "Warmup complete", "Checked", "Ice", "Cobalt", true, false, 12, 16, "start"));
        template.add(new UiComponent(UiComponentType.CARD, "Next exercise", "3 sets - Dumbbell row", "White", "Sunset", true, false, 18, 24, "start"));
        template.add(new UiComponent(UiComponentType.BUTTON, "Start workout", "", "Mint", "White", false, true, 16, 20, "center"));
        return template;
    }

    private void refreshAll() {
        if (selectedIndex < 0 && !components.isEmpty()) {
            selectedIndex = 0;
        }
        if (selectedIndex >= components.size()) {
            selectedIndex = components.isEmpty() ? -1 : components.size() - 1;
        }
        renderPreview();
        layerAdapter.setSelectedPosition(selectedIndex);
        bindInspector();
    }

    private void bindInspector() {
        bindingInspector = true;
        refreshColorDropdownAdapters();
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
        binding.opacitySeekBar.setEnabled(enabled);

        if (selected != null) {
            boolean opacitySupported = supportsOpacity(selected);
            binding.opacityControlGroup.setVisibility(opacitySupported ? View.VISIBLE : View.GONE);
            setInspectorText(binding.componentTitleInput, selected.getTitle());
            setInspectorText(binding.componentHelperInput, selected.getHelper());
            setColorInputValue(binding.backgroundColorInput, selected.getBackgroundColorName());
            setColorInputValue(binding.accentColorInput, selected.getAccentColorName());
            binding.fullWidthSwitch.setChecked(selected.isFullWidth());
            binding.emphasisSwitch.setChecked(selected.isEmphasized());
            binding.widthSeekBar.setProgress(selected.getWidthPercent() - 30);
            binding.heightSeekBar.setProgress(selected.getHeightDp() == 0 ? 0 : selected.getHeightDp() - 31);
            binding.textSizeSeekBar.setProgress(selected.getTextSizeSp() - 10);
            binding.paddingSeekBar.setProgress(selected.getPaddingDp() - 8);
            binding.radiusSeekBar.setProgress(selected.getCornerRadiusDp() - 4);
            binding.opacitySeekBar.setProgress(selected.getOpacityPercent());
            binding.widthValueLabel.setText(getString(R.string.percent_value, selected.getWidthPercent()));
            binding.heightValueLabel.setText(selected.getHeightDp() == 0 ? getString(R.string.auto_value) : getString(R.string.dp_value, selected.getHeightDp()));
            binding.textSizeValueLabel.setText(getString(R.string.sp_value, selected.getTextSizeSp()));
            binding.paddingValueLabel.setText(getString(R.string.dp_value, selected.getPaddingDp()));
            binding.radiusValueLabel.setText(getString(R.string.dp_value, selected.getCornerRadiusDp()));
            binding.opacityValueLabel.setText(getString(R.string.percent_value, selected.getOpacityPercent()));
            if ("center".equals(selected.getAlignment())) {
                binding.alignmentToggle.check(R.id.alignCenterButton);
            } else if ("end".equals(selected.getAlignment())) {
                binding.alignmentToggle.check(R.id.alignEndButton);
            } else {
                binding.alignmentToggle.check(R.id.alignStartButton);
            }
        } else {
            setInspectorText(binding.componentTitleInput, "");
            setInspectorText(binding.componentHelperInput, "");
            setColorInputValue(binding.backgroundColorInput, "");
            setColorInputValue(binding.accentColorInput, "");
            binding.widthValueLabel.setText(getString(R.string.percent_value, 0));
            binding.heightValueLabel.setText(getString(R.string.auto_value));
            binding.textSizeValueLabel.setText(getString(R.string.sp_value, 0));
            binding.paddingValueLabel.setText(getString(R.string.dp_value, 0));
            binding.radiusValueLabel.setText(getString(R.string.dp_value, 0));
            binding.opacityValueLabel.setText(getString(R.string.percent_value, 0));
            binding.opacitySeekBar.setProgress(100);
            binding.opacityControlGroup.setVisibility(View.GONE);
            binding.alignmentToggle.clearChecked();
        }
        bindingInspector = false;
    }

    private void setInspectorText(TextInputEditText input, String value) {
        String safeValue = value == null ? "" : value;
        Editable current = input.getText();
        if (current != null && safeValue.contentEquals(current)) {
            return;
        }
        input.setText(safeValue);
        input.setSelection(safeValue.length());
    }

    private void setColorInputValue(MaterialAutoCompleteTextView input, String colorName) {
        String safeValue = colorName == null ? "" : colorName;
        input.setText(safeValue, false);
        if (safeValue.isEmpty()) {
            input.setCompoundDrawables(null, null, null, null);
            return;
        }
        Drawable swatch = colorSwatchBackground(colorFromName(safeValue));
        swatch.setBounds(0, 0, dp(20), dp(20));
        input.setCompoundDrawables(swatch, null, null, null);
        input.setCompoundDrawablePadding(dp(10));
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
                            openInspectorDrawer();
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

    private int inspectorDrawerWidth() {
        return Math.min(dp(360), Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(28)));
    }

    private void openInspectorDrawer() {
        if (inspectorPopup == null || selectedIndex < 0 || selectedIndex >= components.size()) {
            return;
        }
        inspectorPopup.setWidth(inspectorDrawerWidth());
        if (!inspectorPopup.isShowing()) {
            inspectorPopup.showAtLocation(binding.getRoot(), Gravity.END, 0, 0);
            logDebug("Inspector drawer opened index=" + selectedIndex);
        }
    }

    private void closeInspectorDrawer() {
        if (inspectorPopup != null && inspectorPopup.isShowing()) {
            inspectorPopup.dismiss();
            logDebug("Inspector drawer closed");
        }
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
            case TEXT_AREA:
            case SEARCH_BAR:
            case SLIDER:
            case SNACKBAR:
                return clamp(180, MIN_WIDGET_WIDTH_DP, canvasWidth);
            case FAB:
            case ICON_BUTTON:
            case AVATAR:
            case BADGE:
            case CHIP:
            case LOADING:
            case QR_CODE:
            case GRID_ITEM:
                return clamp(120, MIN_WIDGET_WIDTH_DP, canvasWidth);
            case CHECKBOX:
            case RADIO_GROUP:
            case SWITCH:
            case DIVIDER:
            case PROGRESS:
            case RATING:
            case STEPPER:
            case MEDIA_PLAYER:
                return clamp(190, MIN_WIDGET_WIDTH_DP, canvasWidth);
            case TOP_APP_BAR:
            case BOTTOM_NAV:
            case LIST_ITEM:
            case DIALOG:
            case SKELETON:
            case MAP:
            case CHART:
            case ROW:
            case COLUMN:
            case GRID:
            case SPACER:
                return clamp(240, MIN_WIDGET_WIDTH_DP, canvasWidth);
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
            case TEXT_AREA:
                return 118;
            case SEARCH_BAR:
                return 56;
            case CARD:
                return 118;
            case TOP_APP_BAR:
            case BOTTOM_NAV:
            case SNACKBAR:
                return 64;
            case FAB:
            case ICON_BUTTON:
            case AVATAR:
                return 64;
            case BADGE:
            case CHIP:
                return 42;
            case LIST_ITEM:
                return 82;
            case GRID_ITEM:
            case MENU:
            case DIALOG:
            case COLUMN:
            case QR_CODE:
                return 132;
            case MAP:
            case CHART:
            case GRID:
                return 150;
            case RADIO_GROUP:
            case SLIDER:
            case STEPPER:
            case MEDIA_PLAYER:
                return 76;
            case SKELETON:
                return 92;
            case SPACER:
                return 44;
            case ROW:
                return 78;
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
            case TEXT_AREA:
            case SEARCH_BAR:
            case DROPDOWN:
                return 56;
            case CARD:
                return 72;
            case IMAGE:
                return 80;
            case TABS:
                return 44;
            case CHECKBOX:
            case RADIO_GROUP:
            case SWITCH:
                return 48;
            case PROGRESS:
            case SLIDER:
            case STEPPER:
            case MEDIA_PLAYER:
                return 56;
            case DIVIDER:
            case SPACER:
                return 18;
            case TOP_APP_BAR:
            case BOTTOM_NAV:
            case SNACKBAR:
            case FAB:
            case ICON_BUTTON:
            case AVATAR:
            case BADGE:
            case CHIP:
            case RATING:
            case LOADING:
                return 36;
            case LIST_ITEM:
            case SKELETON:
                return 64;
            case GRID_ITEM:
            case MENU:
            case DIALOG:
            case MAP:
            case CHART:
            case QR_CODE:
            case COLUMN:
            case GRID:
                return 80;
            case ROW:
                return 48;
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

    private boolean supportsOpacity(UiComponent component) {
        return component != null && component.getType() != UiComponentType.SPACER;
    }

    private View createPreviewView(UiComponent component, boolean selected) {
        int background = colorFromName(component.getBackgroundColorName());
        int accent = colorFromName(component.getAccentColorName());
        int padding = dp(component.getPaddingDp());
        int radius = dp(component.getCornerRadiusDp());
        View view;
        switch (component.getType()) {
            case HEADER:
                view = createHeaderPreview(component, background, accent, padding, radius, selected);
                break;
            case TEXT:
                view = createTextPreview(component, background, accent, padding, radius, selected);
                break;
            case BUTTON:
                view = createButtonPreview(component, background, accent, padding, radius, selected);
                break;
            case INPUT:
                view = createInputPreview(component, background, accent, padding, radius, selected);
                break;
            case TEXT_AREA:
                view = createTextAreaPreview(component, background, accent, padding, radius, selected);
                break;
            case SEARCH_BAR:
                view = createSearchBarPreview(component, background, accent, padding, radius, selected);
                break;
            case CARD:
                view = createCardPreview(component, background, accent, padding, radius, selected);
                break;
            case IMAGE:
                view = createImagePreview(component, background, accent, padding, radius, selected);
                break;
            case TABS:
                view = createTabsPreview(component, background, accent, padding, radius, selected);
                break;
            case DROPDOWN:
                view = createDropdownPreview(component, background, accent, padding, radius, selected);
                break;
            case CHECKBOX:
                view = createCheckboxPreview(component, background, accent, padding, radius, selected);
                break;
            case RADIO_GROUP:
                view = createRadioGroupPreview(component, background, accent, padding, radius, selected);
                break;
            case SWITCH:
                view = createSwitchPreview(component, background, accent, padding, radius, selected);
                break;
            case DIVIDER:
                view = createDividerPreview(component, background, accent, padding, radius, selected);
                break;
            case SLIDER:
                view = createSliderPreview(component, background, accent, padding, radius, selected);
                break;
            case TOP_APP_BAR:
                view = createTopAppBarPreview(component, background, accent, padding, radius, selected);
                break;
            case BOTTOM_NAV:
                view = createBottomNavPreview(component, background, accent, padding, radius, selected);
                break;
            case FAB:
                view = createFabPreview(component, background, accent, padding, radius, selected);
                break;
            case ICON_BUTTON:
                view = createIconButtonPreview(component, background, accent, padding, radius, selected);
                break;
            case AVATAR:
                view = createAvatarPreview(component, background, accent, padding, radius, selected);
                break;
            case BADGE:
                view = createBadgePreview(component, background, accent, padding, radius, selected);
                break;
            case CHIP:
                view = createChipPreview(component, background, accent, padding, radius, selected);
                break;
            case LIST_ITEM:
                view = createListItemPreview(component, background, accent, padding, radius, selected);
                break;
            case GRID_ITEM:
                view = createGridItemPreview(component, background, accent, padding, radius, selected);
                break;
            case MENU:
                view = createMenuPreview(component, background, accent, padding, radius, selected);
                break;
            case DIALOG:
                view = createDialogPreview(component, background, accent, padding, radius, selected);
                break;
            case SNACKBAR:
                view = createSnackbarPreview(component, background, accent, padding, radius, selected);
                break;
            case RATING:
                view = createRatingPreview(component, background, accent, padding, radius, selected);
                break;
            case STEPPER:
                view = createStepperPreview(component, background, accent, padding, radius, selected);
                break;
            case LOADING:
                view = createLoadingPreview(component, background, accent, padding, radius, selected);
                break;
            case SKELETON:
                view = createSkeletonPreview(component, background, accent, padding, radius, selected);
                break;
            case MAP:
                view = createMapPreview(component, background, accent, padding, radius, selected);
                break;
            case CHART:
                view = createChartPreview(component, background, accent, padding, radius, selected);
                break;
            case MEDIA_PLAYER:
                view = createMediaPlayerPreview(component, background, accent, padding, radius, selected);
                break;
            case QR_CODE:
                view = createQrPreview(component, background, accent, padding, radius, selected);
                break;
            case SPACER:
                view = createSpacerPreview(component, background, accent, padding, radius, selected);
                break;
            case ROW:
                view = createLayoutPreview(component, background, accent, padding, radius, selected, LinearLayout.HORIZONTAL);
                break;
            case COLUMN:
                view = createLayoutPreview(component, background, accent, padding, radius, selected, LinearLayout.VERTICAL);
                break;
            case GRID:
                view = createGridLayoutPreview(component, background, accent, padding, radius, selected);
                break;
            case PROGRESS:
            default:
                view = createProgressPreview(component, background, accent, padding, radius, selected);
                break;
        }
        if (supportsOpacity(component)) {
            view.setAlpha(component.getOpacityPercent() / 100f);
        }
        return view;
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
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setFocusable(false);
        button.setPadding(padding, padding, padding, padding);
        button.setCornerRadius(radius);
        if (component.isEmphasized()) {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
            button.setTextColor(accent);
        } else {
            button.setStrokeWidth(dp(1));
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(accent));
            button.setTextColor(accent);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
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
        editText.setClickable(false);
        editText.setCursorVisible(false);
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setTextColor(accent);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        editText.setPadding(padding, padding, padding, padding);
        layout.setHint(component.getTitle());
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxCornerRadii(radius, radius, radius, radius);
        layout.setBoxBackgroundColor(background);
        layout.setBoxStrokeColor(selected ? ContextCompat.getColor(this, R.color.accent_cobalt) : accent);
        layout.setBoxStrokeWidth(dp(selected ? 2 : 1));
        layout.setFocusable(false);
        layout.addView(editText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private View createTextAreaPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        View view = createInputPreview(component, background, accent, padding, radius, selected);
        if (view instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) view;
            if (layout.getEditText() != null) {
                layout.getEditText().setSingleLine(false);
                layout.getEditText().setMinLines(3);
                layout.getEditText().setGravity(Gravity.TOP | Gravity.START);
            }
        }
        return view;
    }

    private View createSearchBarPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, padding, selected);
        TextView icon = previewLabel("Search", accent, 12, true);
        icon.setGravity(Gravity.CENTER);
        TextView value = previewLabel(nonEmpty(component.getHelper(), component.getTitle()), adjustAlpha(accent, 0.72f), component.getTextSizeSp(), false);
        value.setSingleLine(true);
        layout.addView(icon, new LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.MATCH_PARENT));
        layout.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
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
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable controlBackground = new GradientDrawable();
        controlBackground.setColor(background);
        controlBackground.setCornerRadius(radius);
        controlBackground.setStroke(dp(selected ? 2 : 1),
                selected ? ContextCompat.getColor(this, R.color.accent_cobalt) : accent);
        layout.setBackground(controlBackground);
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
            label.setSingleLine(true);
            GradientDrawable tabBg = new GradientDrawable();
            tabBg.setCornerRadius(dp(Math.max(8, component.getCornerRadiusDp() - 4)));
            tabBg.setColor(active ? accent : Color.TRANSPARENT);
            label.setBackground(tabBg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            params.setMargins(dp(2), 0, dp(2), 0);
            layout.addView(label, params);
        }
        return layout;
    }

    private View createDropdownPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        TextInputLayout layout = new TextInputLayout(this);
        AppCompatEditText value = new AppCompatEditText(this);
        value.setText(nonEmpty(component.getHelper(), component.getTitle()));
        value.setFocusable(false);
        value.setClickable(false);
        value.setCursorVisible(false);
        value.setBackgroundColor(Color.TRANSPARENT);
        value.setTextColor(accent);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        value.setPadding(padding, padding / 2, padding, padding / 2);
        layout.setHint(component.getTitle());
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxCornerRadii(radius, radius, radius, radius);
        layout.setBoxBackgroundColor(background);
        layout.setBoxStrokeColor(selected ? ContextCompat.getColor(this, R.color.accent_cobalt) : accent);
        layout.setBoxStrokeWidth(dp(selected ? 2 : 1));
        layout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        layout.setEndIconTintList(ColorStateList.valueOf(accent));
        layout.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private View createCheckboxPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, Math.max(10, component.getCornerRadiusDp()));
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(component.getTitle());
        checkBox.setTextColor(accent);
        checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        checkBox.setChecked(isAffirmative(component.getHelper()));
        checkBox.setButtonTintList(ColorStateList.valueOf(accent));
        checkBox.setPadding(Math.max(padding / 2, dp(4)), 0, Math.max(padding / 2, dp(4)), 0);
        preparePassivePreviewChild(checkBox);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL);
        host.addView(checkBox, params);
        return host;
    }

    private View createRadioGroupPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(6), padding / 2), selected);
        String[] options = splitItems(component.getHelper(), "Free, Pro, Team");
        for (int i = 0; i < Math.min(options.length, 3); i++) {
            LinearLayout option = new LinearLayout(this);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setOrientation(LinearLayout.HORIZONTAL);
            option.addView(radioDot(i == 0, accent), new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView label = previewLabel(options[i], accent, Math.max(10, component.getTextSizeSp() - 1), false);
            label.setSingleLine(true);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.setMargins(dp(5), 0, dp(4), 0);
            option.addView(label, labelParams);
            layout.addView(option, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        return layout;
    }

    private View createSwitchPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, Math.max(10, component.getCornerRadiusDp()));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(Math.max(padding / 2, dp(4)), 0, Math.max(padding / 2, dp(4)), 0);
        TextView label = new TextView(this);
        label.setText(component.getTitle());
        label.setTextColor(accent);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        SwitchMaterial switchView = new SwitchMaterial(this);
        switchView.setChecked(isAffirmative(component.getHelper()));
        switchView.setThumbTintList(ColorStateList.valueOf(accent));
        switchView.setTrackTintList(ColorStateList.valueOf(adjustAlpha(accent, 0.32f)));
        preparePassivePreviewChild(switchView);
        layout.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        layout.addView(switchView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        host.addView(layout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return host;
    }

    private View createDividerPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, Math.max(4, component.getCornerRadiusDp()));
        View line = new View(this);
        line.setBackgroundColor(accent);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(component.isEmphasized() ? 3 : 1),
                Gravity.CENTER);
        params.setMargins(Math.max(padding / 2, dp(4)), 0, Math.max(padding / 2, dp(4)), 0);
        host.addView(line, params);
        return host;
    }

    private View createProgressPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, Math.max(10, component.getCornerRadiusDp()));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(Math.max(padding / 2, dp(6)), 0, Math.max(padding / 2, dp(6)), 0);
        TextView label = new TextView(this);
        label.setText(component.getTitle());
        label.setTextColor(accent);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, component.getTextSizeSp());
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(parsePercent(component.getHelper(), 65));
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(adjustAlpha(accent, 0.2f)));
        TextView value = new TextView(this);
        value.setText(component.getHelper());
        value.setGravity(Gravity.END);
        value.setTextColor(adjustAlpha(accent, 0.72f));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(10, component.getTextSizeSp() - 2));
        layout.addView(label);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12));
        progressParams.setMargins(0, dp(3), 0, 0);
        layout.addView(progress, progressParams);
        layout.addView(value);
        host.addView(layout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return host;
    }

    private View createSliderPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(8), padding / 2), selected);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = previewLabel(component.getTitle(), accent, component.getTextSizeSp(), true);
        TextView value = previewLabel(component.getHelper(), adjustAlpha(accent, 0.72f), Math.max(10, component.getTextSizeSp() - 2), false);
        value.setGravity(Gravity.END);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(value, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(titleRow);
        layout.addView(trackView(accent, parsePercent(component.getHelper(), 60)), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24)));
        return layout;
    }

    private View createTopAppBarPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(8), padding / 2), selected);
        TextView menu = previewLabel("Menu", accent, 11, true);
        menu.setGravity(Gravity.CENTER);
        TextView title = previewLabel(component.getTitle(), accent, component.getTextSizeSp(), true);
        title.setSingleLine(true);
        TextView actions = previewLabel("Search  User", accent, 11, false);
        actions.setGravity(Gravity.CENTER);
        layout.addView(menu, new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.MATCH_PARENT));
        layout.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        layout.addView(actions, new LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.MATCH_PARENT));
        return layout;
    }

    private View createBottomNavPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(6), padding / 2), selected);
        String[] items = splitItems(component.getTitle(), "Home, Search, Profile");
        String activeItem = nonEmpty(component.getHelper(), items[0]);
        for (String item : items) {
            TextView label = previewLabel(item.trim(), item.trim().equalsIgnoreCase(activeItem) ? accent : adjustAlpha(accent, 0.55f), component.getTextSizeSp(), item.trim().equalsIgnoreCase(activeItem));
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            layout.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        return layout;
    }

    private View createFabPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, 36);
        TextView fab = previewLabel(nonEmpty(component.getTitle(), "+"), accent, component.getTextSizeSp(), true);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(ovalBackground(background, Color.TRANSPARENT, 0));
        host.addView(fab, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        return host;
    }

    private View createIconButtonPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, Math.max(12, component.getCornerRadiusDp()));
        TextView icon = previewLabel(shortToken(component.getTitle()), accent, component.getTextSizeSp(), true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundBackground(background, accent, radius, dp(1)));
        host.addView(icon, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        return host;
    }

    private View createAvatarPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, 36);
        TextView avatar = previewLabel(nonEmpty(component.getHelper(), initials(component.getTitle())), accent, component.getTextSizeSp(), true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(ovalBackground(background, accent, dp(1)));
        host.addView(avatar, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
        return host;
    }

    private View createBadgePreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, 20);
        TextView badge = previewLabel(nonEmpty(component.getHelper(), component.getTitle()), accent, component.getTextSizeSp(), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), 0, dp(10), 0);
        badge.setBackground(roundBackground(background, Color.TRANSPARENT, dp(20), 0));
        host.addView(badge, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(34), Gravity.CENTER));
        return host;
    }

    private View createChipPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, 20);
        TextView chip = previewLabel(component.getTitle(), accent, component.getTextSizeSp(), component.isEmphasized());
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(roundBackground(background, accent, dp(20), dp(1)));
        host.addView(chip, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(38), Gravity.CENTER));
        return host;
    }

    private View createListItemPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(8), padding / 2), selected);
        TextView avatar = previewLabel(shortToken(component.getTitle()), background, 13, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(ovalBackground(accent, Color.TRANSPARENT, 0));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.addView(previewLabel(component.getTitle(), accent, component.getTextSizeSp(), true));
        texts.addView(previewLabel(component.getHelper(), adjustAlpha(accent, 0.68f), Math.max(10, component.getTextSizeSp() - 3), false));
        layout.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        textParams.setMargins(dp(10), 0, 0, 0);
        layout.addView(texts, textParams);
        return layout;
    }

    private View createGridItemPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(8), padding / 2), selected);
        View image = new View(this);
        image.setBackground(roundBackground(adjustAlpha(accent, 0.16f), accent, dp(12), dp(1)));
        layout.addView(image, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView title = previewLabel(component.getTitle(), accent, component.getTextSizeSp(), true);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);
        TextView helper = previewLabel(component.getHelper(), adjustAlpha(accent, 0.72f), Math.max(10, component.getTextSizeSp() - 2), false);
        helper.setGravity(Gravity.CENTER);
        layout.addView(helper);
        return layout;
    }

    private View createMenuPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(8), padding / 2), selected);
        for (String item : splitItems(component.getHelper(), "Edit, Share, Delete")) {
            TextView label = previewLabel(item.trim(), accent, Math.max(12, component.getTextSizeSp() - 1), false);
            label.setPadding(dp(6), dp(4), dp(6), dp(4));
            layout.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return layout;
    }

    private View createDialogPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(10), padding / 2), selected);
        layout.addView(previewLabel(component.getTitle(), accent, component.getTextSizeSp(), true));
        TextView body = previewLabel(component.getHelper(), adjustAlpha(accent, 0.72f), Math.max(10, component.getTextSizeSp() - 4), false);
        body.setPadding(0, dp(6), 0, dp(8));
        layout.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.addView(previewLabel("Cancel", accent, 12, true));
        TextView ok = previewLabel("OK", accent, 12, true);
        ok.setPadding(dp(16), 0, 0, 0);
        actions.addView(ok);
        layout.addView(actions);
        return layout;
    }

    private View createSnackbarPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(10), padding / 2), selected);
        layout.addView(previewLabel(component.getTitle(), accent, component.getTextSizeSp(), false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        TextView action = previewLabel(component.getHelper(), accent, component.getTextSizeSp(), true);
        action.setGravity(Gravity.CENTER);
        layout.addView(action, new LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.MATCH_PARENT));
        return layout;
    }

    private View createRatingPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(8), padding / 2), selected);
        int rating = clamp(parsePercent(component.getHelper(), 4), 0, 5);
        if (rating > 5) {
            rating = 4;
        }
        TextView stars = previewLabel(ratingStars(rating), accent, component.getTextSizeSp(), true);
        stars.setGravity(Gravity.CENTER);
        layout.addView(stars, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        return layout;
    }

    private View createStepperPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(8), padding / 2), selected);
        for (int i = 1; i <= 4; i++) {
            TextView step = previewLabel(String.valueOf(i), i <= 2 ? background : accent, 12, true);
            step.setGravity(Gravity.CENTER);
            step.setBackground(ovalBackground(i <= 2 ? accent : Color.TRANSPARENT, accent, dp(1)));
            LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(dp(26), dp(26));
            stepParams.setMargins(0, 0, dp(6), 0);
            layout.addView(step, stepParams);
        }
        TextView label = previewLabel(component.getHelper(), accent, Math.max(10, component.getTextSizeSp() - 1), false);
        label.setSingleLine(true);
        layout.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        return layout;
    }

    private View createLoadingPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(8), padding / 2), selected);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        layout.addView(progress, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView label = previewLabel(component.getTitle(), accent, component.getTextSizeSp(), false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        labelParams.setMargins(dp(10), 0, 0, 0);
        layout.addView(label, labelParams);
        return layout;
    }

    private View createSkeletonPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(8), padding / 2), selected);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 3; i++) {
            View bar = new View(this);
            bar.setBackground(roundBackground(adjustAlpha(accent, 0.22f), Color.TRANSPARENT, dp(8), 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(i == 2 ? dp(120) : LinearLayout.LayoutParams.MATCH_PARENT, dp(14));
            params.setMargins(0, dp(4), 0, dp(4));
            layout.addView(bar, params);
        }
        return layout;
    }

    private View createMapPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = new FrameLayout(this);
        host.setPadding(padding / 2, padding / 2, padding / 2, padding / 2);
        host.setBackground(roundBackground(background, selected ? ContextCompat.getColor(this, R.color.accent_cobalt) : accent, radius, dp(selected ? 2 : 1)));
        TextView pin = previewLabel("PIN", accent, component.getTextSizeSp(), true);
        pin.setGravity(Gravity.CENTER);
        pin.setBackground(ovalBackground(adjustAlpha(accent, 0.16f), accent, dp(1)));
        host.addView(pin, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
        return host;
    }

    private View createChartPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(8), padding / 2), selected);
        layout.setGravity(Gravity.BOTTOM);
        LinearLayout bars = new LinearLayout(this);
        bars.setGravity(Gravity.BOTTOM);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        int[] heights = {34, 58, 42, 76};
        for (int height : heights) {
            View bar = new View(this);
            bar.setBackground(roundBackground(accent, Color.TRANSPARENT, dp(6), 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
            params.setMargins(dp(4), 0, dp(4), 0);
            bars.addView(bar, params);
        }
        layout.addView(previewLabel(component.getTitle(), accent, Math.max(10, component.getTextSizeSp() - 1), true));
        layout.addView(bars, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return layout;
    }

    private View createMediaPlayerPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = horizontalShell(background, accent, radius, Math.max(dp(8), padding / 2), selected);
        TextView play = previewLabel(">", background, 18, true);
        play.setGravity(Gravity.CENTER);
        play.setBackground(ovalBackground(accent, Color.TRANSPARENT, 0));
        layout.addView(play, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(previewLabel(component.getTitle(), accent, component.getTextSizeSp(), true));
        body.addView(trackView(accent, parsePercent(component.getHelper(), 32)), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));
        body.addView(previewLabel(component.getHelper(), adjustAlpha(accent, 0.72f), 10, false));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        bodyParams.setMargins(dp(10), 0, 0, 0);
        layout.addView(body, bodyParams);
        return layout;
    }

    private View createQrPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout grid = shellLayout(background, radius, Math.max(dp(6), padding / 2), selected);
        grid.setGravity(Gravity.CENTER);
        for (int row = 0; row < 5; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 5; col++) {
                View cell = new View(this);
                boolean filled = row == 0 || col == 0 || row == 4 || col == 4 || row == col || (row + col == 4);
                cell.setBackgroundColor(filled ? accent : background);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(dp(12), dp(12));
                cellParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                line.addView(cell, cellParams);
            }
            grid.addView(line);
        }
        return grid;
    }

    private View createSpacerPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        FrameLayout host = controlHost(selected, Math.max(4, component.getCornerRadiusDp()));
        View line = new View(this);
        line.setBackgroundColor(adjustAlpha(accent, 0.45f));
        host.addView(line, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(2), Gravity.CENTER));
        return host;
    }

    private View createLayoutPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected, int orientation) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(8), padding / 2), selected);
        layout.setOrientation(orientation);
        layout.setGravity(Gravity.CENTER);
        String[] items = splitItems(component.getHelper(), "Item A, Item B");
        for (String item : items) {
            TextView box = previewLabel(item.trim(), accent, Math.max(10, component.getTextSizeSp() - 1), false);
            box.setGravity(Gravity.CENTER);
            box.setBackground(roundBackground(adjustAlpha(accent, 0.12f), accent, dp(10), dp(1)));
            LinearLayout.LayoutParams params = orientation == LinearLayout.HORIZONTAL
                    ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                    : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            layout.addView(box, params);
        }
        return layout;
    }

    private View createGridLayoutPreview(UiComponent component, int background, int accent, int padding, int radius, boolean selected) {
        LinearLayout layout = shellLayout(background, radius, Math.max(dp(8), padding / 2), selected);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                TextView cell = previewLabel(row + "," + col, accent, 10, false);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(roundBackground(adjustAlpha(accent, 0.12f), accent, dp(10), dp(1)));
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                cellParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                line.addView(cell, cellParams);
            }
            layout.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return layout;
    }

    private LinearLayout horizontalShell(int background, int accent, int radius, int padding, boolean selected) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(padding, padding / 2, padding, padding / 2);
        layout.setBackground(roundBackground(
                background,
                selected ? ContextCompat.getColor(this, R.color.accent_cobalt) : accent,
                radius,
                dp(selected ? 2 : 1)));
        return layout;
    }

    private TextView previewLabel(String text, int color, int textSizeSp, boolean bold) {
        TextView label = new TextView(this);
        label.setText(text == null ? "" : text);
        label.setTextColor(color);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return label;
    }

    private GradientDrawable roundBackground(int color, int strokeColor, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private GradientDrawable ovalBackground(int color, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private View radioDot(boolean checked, int accent) {
        FrameLayout dot = new FrameLayout(this);
        dot.setBackground(ovalBackground(Color.TRANSPARENT, accent, dp(2)));
        if (checked) {
            View center = new View(this);
            center.setBackground(ovalBackground(accent, Color.TRANSPARENT, 0));
            dot.addView(center, new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.CENTER));
        }
        return dot;
    }

    private View trackView(int accent, int progressPercent) {
        FrameLayout track = new FrameLayout(this);
        View base = new View(this);
        base.setBackground(roundBackground(adjustAlpha(accent, 0.22f), Color.TRANSPARENT, dp(5), 0));
        FrameLayout.LayoutParams baseParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(5),
                Gravity.CENTER);
        track.addView(base, baseParams);

        View active = new View(this);
        active.setBackground(roundBackground(accent, Color.TRANSPARENT, dp(5), 0));
        FrameLayout.LayoutParams activeParams = new FrameLayout.LayoutParams(
                Math.max(dp(24), Math.round(canvasWidthDp() * (clamp(progressPercent, 0, 100) / 100f) * 0.45f)),
                dp(5),
                Gravity.CENTER_VERTICAL | Gravity.START);
        track.addView(active, activeParams);

        View thumb = new View(this);
        thumb.setBackground(ovalBackground(accent, Color.TRANSPARENT, 0));
        FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(
                dp(14),
                dp(14),
                Gravity.CENTER_VERTICAL | Gravity.START);
        thumbParams.leftMargin = Math.max(0, activeParams.width - dp(7));
        track.addView(thumb, thumbParams);
        return track;
    }

    private String[] splitItems(String value, String fallback) {
        String source = value == null || value.trim().isEmpty() ? fallback : value;
        String[] raw = source.split(",");
        List<String> items = new ArrayList<>();
        for (String item : raw) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items.isEmpty() ? new String[]{"Item"} : items.toArray(new String[0]);
    }

    private String shortToken(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "UI";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 2 ? trimmed.toUpperCase(Locale.US) : trimmed.substring(0, 1).toUpperCase(Locale.US);
    }

    private String initials(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "UI";
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1) {
            return shortToken(parts[0]);
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.US);
    }

    private String ratingStars(int rating) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            if (i > 1) {
                builder.append(' ');
            }
            builder.append(i <= rating ? '*' : '-');
        }
        return builder.toString();
    }

    private FrameLayout controlHost(boolean selected, int radiusDp) {
        FrameLayout host = new FrameLayout(this);
        host.setClipChildren(false);
        host.setClipToPadding(false);
        if (selected) {
            int inset = dp(2);
            host.setPadding(inset, inset, inset, inset);
            GradientDrawable selection = new GradientDrawable();
            selection.setColor(Color.TRANSPARENT);
            selection.setCornerRadius(dp(radiusDp));
            selection.setStroke(dp(2), ContextCompat.getColor(this, R.color.accent_cobalt));
            host.setBackground(selection);
        }
        return host;
    }

    private void preparePassivePreviewChild(View child) {
        child.setClickable(false);
        child.setLongClickable(false);
        child.setFocusable(false);
        child.setFocusableInTouchMode(false);
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int paddingHorizontal = dp(28);
        int paddingVertical = dp(26);
        card.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, dp(18));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#13231F"));
        background.setCornerRadius(dp(28));
        background.setStroke(dp(1), Color.parseColor("#1E3A34"));
        card.setBackground(background);

        TextView title = aboutText(getString(R.string.about_title), 30, true);
        card.addView(title);
        addVerticalSpace(card, 56);
        card.addView(aboutText(getString(R.string.about_developer), 19, false));
        addVerticalSpace(card, 28);
        card.addView(aboutText(
                getString(R.string.about_version, getAppVersionName(), getAppVersionCode(), getBuildTypeName()),
                19,
                false));
        addVerticalSpace(card, 28);
        card.addView(aboutLinkText(
                getString(R.string.about_email_label),
                getString(R.string.about_email),
                "mailto:" + getString(R.string.about_email)));
        addVerticalSpace(card, 28);
        card.addView(aboutLinkText(
                getString(R.string.about_github_label),
                getString(R.string.about_github_url),
                getString(R.string.about_github_url)));
        addVerticalSpace(card, 48);

        MaterialButton closeButton = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        closeButton.setText(getString(R.string.close_label).toUpperCase(Locale.US));
        closeButton.setTextColor(Color.parseColor("#3FA35B"));
        closeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        closeButton.setAllCaps(false);
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48));
        closeParams.gravity = Gravity.END;
        card.addView(closeButton, closeParams);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(card)
                .create();
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(dialogInterface -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.62f);
                window.setLayout(
                        getResources().getDisplayMetrics().widthPixels - dp(36),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
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

    private long getAppVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return getPackageManager()
                        .getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0))
                        .getLongVersionCode();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            logError("Could not read app version code", e);
            return 0;
        }
    }

    private String getBuildTypeName() {
        return getString(R.string.about_build_debug);
    }

    private TextView aboutText(String text, int sizeSp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        textView.setLineSpacing(dp(3), 1.0f);
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    private TextView aboutLinkText(String label, String value, String uri) {
        TextView textView = aboutText(label + value, 19, false);
        int start = label.length();
        SpannableString text = new SpannableString(label + value);
        text.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openExternalUri(uri);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#C76542"));
                ds.setUnderlineText(true);
            }
        }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(text);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setHighlightColor(Color.TRANSPARENT);
        return textView;
    }

    private void openExternalUri(String uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (RuntimeException e) {
            logError("Could not open external URI " + uri, e);
        }
    }

    private void addVerticalSpace(LinearLayout parent, int heightDp) {
        Space space = new Space(this);
        parent.addView(space, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)));
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
            item.put("opacityPercent", component.getOpacityPercent());
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
                component.setOpacityPercent(item.optInt("opacityPercent", 100));
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
        openInspectorDrawer();
    }

    @Override
    public void onDelete(int position) {
        selectedIndex = position;
        logDebug("Layer delete requested index=" + position);
        deleteSelected();
    }

    @ColorInt
    private int colorFromName(String name) {
        if (isHexColorName(name)) {
            try {
                return Color.parseColor(name.trim());
            } catch (IllegalArgumentException ignored) {
                return Color.WHITE;
            }
        }
        Integer color = palette.get(name);
        return color != null ? color : Color.WHITE;
    }

    private boolean isHexColorName(String name) {
        return name != null && name.trim().matches(HEX_COLOR_PATTERN);
    }

    private String normalizeHexColorName(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private GradientDrawable colorSwatchBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(5));
        drawable.setStroke(dp(1), ContextCompat.getColor(this, R.color.stroke_soft));
        return drawable;
    }

    private GradientDrawable customColorOptionBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(dp(5));
        drawable.setStroke(dp(2), ContextCompat.getColor(this, R.color.accent_cobalt));
        return drawable;
    }

    private final class ColorDropdownAdapter extends ArrayAdapter<String> {
        private ColorDropdownAdapter(List<String> colors) {
            super(MainActivity.this, R.layout.item_color_dropdown, R.id.colorNameText, colors);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return colorRow(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return colorRow(position, convertView, parent);
        }

        private View colorRow(int position, View convertView, ViewGroup parent) {
            View row = convertView != null
                    ? convertView
                    : getLayoutInflater().inflate(R.layout.item_color_dropdown, parent, false);
            String colorName = getItem(position);
            if (colorName == null) {
                colorName = "";
            }
            TextView label = row.findViewById(R.id.colorNameText);
            View swatch = row.findViewById(R.id.colorSwatch);
            label.setText(colorName);
            swatch.setBackground(getString(R.string.custom_color_option).equals(colorName)
                    ? customColorOptionBackground()
                    : colorSwatchBackground(colorFromName(colorName)));
            return row;
        }
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
