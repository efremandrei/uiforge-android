package com.uiforge.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements LayerAdapter.LayerActionListener {
    private static final String STATE_PROJECT_NAME = "project_name";
    private static final String STATE_COMPONENTS = "components";
    private static final String STATE_SELECTION = "selection";
    private static final String PROJECTS_DIR = "projects";
    private static final String PROJECT_EXTENSION = ".uiforge.json";

    private ActivityMainBinding binding;
    private final List<UiComponent> components = new ArrayList<>();
    private final Map<String, Integer> palette = new LinkedHashMap<>();
    private LayerAdapter layerAdapter;
    private boolean bindingInspector;
    private boolean dragInProgress;
    private int selectedIndex = -1;
    private int dragTargetIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.BLACK);

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
        binding.saveProjectButton.setOnClickListener(v -> showSaveProjectDialog());
        binding.loadProjectButton.setOnClickListener(v -> showLoadProjectDialog());
        binding.exportButton.setOnClickListener(v -> showExportDialog());
    }

    private void setupHelpButtons() {
        binding.heroHelpButton.setOnClickListener(v -> showHelpDialog(R.string.app_name, R.string.help_overview));
        binding.paletteHelpButton.setOnClickListener(v -> showHelpDialog(R.string.palette_title, R.string.help_palette));
        binding.templatesHelpButton.setOnClickListener(v -> showHelpDialog(R.string.templates_title, R.string.help_templates));
        binding.previewHelpButton.setOnClickListener(v -> showHelpDialog(R.string.preview_title, R.string.help_preview));
        binding.layersHelpButton.setOnClickListener(v -> showHelpDialog(R.string.layers_title, R.string.help_layers));
        binding.inspectorHelpButton.setOnClickListener(v -> showHelpDialog(R.string.inspector_title, R.string.help_inspector));
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
                    updateSelected(component -> component.setWidthPercent(width));
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
        components.clear();
        components.addAll(template);
        binding.projectNameInput.setText(projectName);
        selectedIndex = components.isEmpty() ? -1 : 0;
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
            binding.previewCanvas.addView(createEmptyCanvasState());
            return;
        }
        for (int i = 0; i < components.size(); i++) {
            if (dragInProgress && dragTargetIndex == i) {
                binding.previewCanvas.addView(createDropIndicator(), dropIndicatorLayoutParams());
            }
            UiComponent component = components.get(i);
            View preview = createPreviewView(component, i == selectedIndex);
            preview.setTag(Integer.valueOf(i));
            preview.setOnClickListener(v -> {
                selectedIndex = (Integer) v.getTag();
                refreshAll();
            });
            preview.setOnLongClickListener(v -> startExistingComponentDrag(v, (Integer) v.getTag()));

            int previewWidth = binding.previewCanvas.getWidth() > 0 ? binding.previewCanvas.getWidth() : dp(252);
            int width = component.isFullWidth()
                    ? LinearLayout.LayoutParams.MATCH_PARENT
                    : Math.max(dp(96), Math.round(previewWidth * (component.getWidthPercent() / 100f)));
            int height = component.getHeightDp() == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : dp(component.getHeightDp());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.topMargin = dp(i == 0 && (!dragInProgress || dragTargetIndex != 0) ? 0 : 14);
            params.gravity = resolveGravity(component.getAlignment());
            binding.previewCanvas.addView(preview, params);
        }
        if (dragInProgress && dragTargetIndex == components.size()) {
            binding.previewCanvas.addView(createDropIndicator(), dropIndicatorLayoutParams());
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

    private View createDropIndicator() {
        View indicator = new View(this);
        indicator.setBackgroundResource(R.drawable.bg_drop_indicator);
        return indicator;
    }

    private LinearLayout.LayoutParams dropIndicatorLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(6));
        params.topMargin = dp(14);
        return params;
    }

    private boolean startExistingComponentDrag(View source, int index) {
        if (index < 0 || index >= components.size()) {
            return false;
        }
        selectedIndex = index;
        layerAdapter.setSelectedPosition(selectedIndex);
        bindInspector();
        return startDrag(source, new DragPayload(null, index));
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
                dragInProgress = true;
                dragTargetIndex = components.isEmpty() ? 0 : components.size();
                renderPreview();
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                updateDragTarget(resolveDropIndex(event.getY()));
                return true;
            case DragEvent.ACTION_DROP:
                applyDrop(payload, resolveDropIndex(event.getY()));
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                dragInProgress = false;
                dragTargetIndex = -1;
                refreshAll();
                return true;
            default:
                return true;
        }
    }

    private void updateDragTarget(int targetIndex) {
        int normalized = clamp(targetIndex, 0, components.size());
        if (normalized != dragTargetIndex) {
            dragTargetIndex = normalized;
            renderPreview();
        }
    }

    private int resolveDropIndex(float y) {
        int index = 0;
        for (int i = 0; i < binding.previewCanvas.getChildCount(); i++) {
            View child = binding.previewCanvas.getChildAt(i);
            Object tag = child.getTag();
            if (!(tag instanceof Integer)) {
                continue;
            }
            float midpoint = child.getTop() + (child.getHeight() / 2f);
            if (y < midpoint) {
                return index;
            }
            index++;
        }
        return index;
    }

    private void applyDrop(DragPayload payload, int targetIndex) {
        int clamped = clamp(targetIndex, 0, components.size());
        if (payload.isPaletteDrag()) {
            components.add(clamped, UiComponent.createDefault(payload.type));
            selectedIndex = clamped;
            return;
        }
        if (payload.sourceIndex < 0 || payload.sourceIndex >= components.size()) {
            return;
        }
        UiComponent moved = components.remove(payload.sourceIndex);
        if (clamped > payload.sourceIndex) {
            clamped--;
        }
        clamped = clamp(clamped, 0, components.size());
        components.add(clamped, moved);
        selectedIndex = clamped;
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
            default:
                return createImagePreview(component, background, accent, padding, radius, selected);
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
        components.remove(selectedIndex);
        if (components.isEmpty()) {
            selectedIndex = -1;
        } else if (selectedIndex >= components.size()) {
            selectedIndex = components.size() - 1;
        }
        refreshAll();
    }

    private void showExportDialog() {
        try {
            String payload = buildProjectJson().toString(2);
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
        } catch (IOException | JSONException e) {
            Toast.makeText(this, R.string.project_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoadProjectDialog() {
        File[] savedProjects = listSavedProjectFiles();
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
        try (FileInputStream stream = new FileInputStream(projectFile)) {
            byte[] bytes = new byte[(int) projectFile.length()];
            int read = stream.read(bytes);
            if (read <= 0) {
                throw new IOException("Empty file");
            }
            JSONObject root = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
            applyProjectJson(root);
            Toast.makeText(this, R.string.project_loaded, Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException e) {
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
            item.put("heightDp", component.getHeightDp());
            item.put("textSizeSp", component.getTextSizeSp());
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
                component.setHeightDp(item.optInt("heightDp", component.getHeightDp()));
                component.setTextSizeSp(item.optInt("textSizeSp", component.getTextSizeSp()));
                components.add(component);
            }
        }
        binding.projectNameInput.setText(root.optString("projectName", "Untitled Flow"));
        selectedIndex = components.isEmpty() ? -1 : 0;
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
        refreshAll();
    }

    @Override
    public void onDelete(int position) {
        selectedIndex = position;
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
