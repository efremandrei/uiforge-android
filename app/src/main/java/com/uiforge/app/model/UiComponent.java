package com.uiforge.app.model;

import java.io.Serializable;

public class UiComponent implements Serializable {
    private UiComponentType type;
    private String title;
    private String helper;
    private String backgroundColorName;
    private String accentColorName;
    private boolean fullWidth;
    private boolean emphasized;
    private int paddingDp;
    private int cornerRadiusDp;
    private String alignment;

    public UiComponent(UiComponentType type, String title, String helper, String backgroundColorName,
                       String accentColorName, boolean fullWidth, boolean emphasized, int paddingDp,
                       int cornerRadiusDp, String alignment) {
        this.type = type;
        this.title = title;
        this.helper = helper;
        this.backgroundColorName = backgroundColorName;
        this.accentColorName = accentColorName;
        this.fullWidth = fullWidth;
        this.emphasized = emphasized;
        this.paddingDp = paddingDp;
        this.cornerRadiusDp = cornerRadiusDp;
        this.alignment = alignment;
    }

    public static UiComponent createDefault(UiComponentType type) {
        switch (type) {
            case HEADER:
                return new UiComponent(type, "Hero headline", "A short explanation for the screen", "Sand", "Cobalt", true, true, 20, 24, "start");
            case TEXT:
                return new UiComponent(type, "Body copy", "Explain the value in one focused sentence", "Ice", "Ink", true, false, 16, 18, "start");
            case BUTTON:
                return new UiComponent(type, "Continue", "", "Cobalt", "Sand", true, true, 18, 20, "center");
            case INPUT:
                return new UiComponent(type, "Email address", "you@example.com", "White", "Mint", true, false, 14, 18, "start");
            case CARD:
                return new UiComponent(type, "Feature card", "Highlight one benefit or next step", "White", "Sunset", true, false, 18, 24, "start");
            case IMAGE:
            default:
                return new UiComponent(type, "Image placeholder", "16:9 visual area", "Charcoal", "Gold", true, false, 18, 28, "center");
        }
    }

    public UiComponentType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHelper() {
        return helper;
    }

    public void setHelper(String helper) {
        this.helper = helper;
    }

    public String getBackgroundColorName() {
        return backgroundColorName;
    }

    public void setBackgroundColorName(String backgroundColorName) {
        this.backgroundColorName = backgroundColorName;
    }

    public String getAccentColorName() {
        return accentColorName;
    }

    public void setAccentColorName(String accentColorName) {
        this.accentColorName = accentColorName;
    }

    public boolean isFullWidth() {
        return fullWidth;
    }

    public void setFullWidth(boolean fullWidth) {
        this.fullWidth = fullWidth;
    }

    public boolean isEmphasized() {
        return emphasized;
    }

    public void setEmphasized(boolean emphasized) {
        this.emphasized = emphasized;
    }

    public int getPaddingDp() {
        return paddingDp;
    }

    public void setPaddingDp(int paddingDp) {
        this.paddingDp = paddingDp;
    }

    public int getCornerRadiusDp() {
        return cornerRadiusDp;
    }

    public void setCornerRadiusDp(int cornerRadiusDp) {
        this.cornerRadiusDp = cornerRadiusDp;
    }

    public String getAlignment() {
        return alignment;
    }

    public void setAlignment(String alignment) {
        this.alignment = alignment;
    }

    public String getSummary() {
        if (helper == null || helper.isEmpty()) {
            return type.getLabel();
        }
        return type.getLabel() + " - " + helper;
    }
}
