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
    private int widthPercent = 100;
    private int widthDp = 0;
    private int heightDp = 0;
    private int textSizeSp = 16;
    private int xDp = 12;
    private int yDp = 16;

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
        applyTypeDefaults(type);
    }

    private void applyTypeDefaults(UiComponentType type) {
        switch (type) {
            case HEADER:
                textSizeSp = 26;
                break;
            case TEXT:
                textSizeSp = 17;
                break;
            case BUTTON:
                heightDp = 54;
                textSizeSp = 15;
                break;
            case INPUT:
            case SEARCH_BAR:
                heightDp = 56;
                textSizeSp = 16;
                break;
            case TEXT_AREA:
                heightDp = 118;
                textSizeSp = 16;
                break;
            case TABS:
                heightDp = 48;
                textSizeSp = 14;
                break;
            case DROPDOWN:
                heightDp = 56;
                textSizeSp = 15;
                break;
            case CHECKBOX:
            case RADIO_GROUP:
            case SWITCH:
                heightDp = 52;
                textSizeSp = 15;
                break;
            case DIVIDER:
                heightDp = 22;
                textSizeSp = 12;
                break;
            case PROGRESS:
                heightDp = 70;
                textSizeSp = 14;
                break;
            case SLIDER:
            case STEPPER:
            case MEDIA_PLAYER:
                heightDp = 76;
                textSizeSp = 14;
                break;
            case TOP_APP_BAR:
            case BOTTOM_NAV:
            case SNACKBAR:
                heightDp = 64;
                textSizeSp = 14;
                break;
            case FAB:
            case ICON_BUTTON:
            case AVATAR:
                heightDp = 64;
                textSizeSp = 18;
                break;
            case BADGE:
            case CHIP:
                heightDp = 42;
                textSizeSp = 13;
                break;
            case LIST_ITEM:
                heightDp = 82;
                textSizeSp = 16;
                break;
            case GRID_ITEM:
            case MENU:
            case DIALOG:
                heightDp = 132;
                textSizeSp = 16;
                break;
            case RATING:
            case LOADING:
                heightDp = 58;
                textSizeSp = 14;
                break;
            case SKELETON:
                heightDp = 92;
                textSizeSp = 14;
                break;
            case MAP:
            case CHART:
            case GRID:
                heightDp = 150;
                textSizeSp = 16;
                break;
            case QR_CODE:
            case COLUMN:
                heightDp = 128;
                textSizeSp = 14;
                break;
            case SPACER:
                heightDp = 44;
                textSizeSp = 12;
                break;
            case ROW:
                heightDp = 78;
                textSizeSp = 14;
                break;
            case CARD:
                textSizeSp = 18;
                break;
            case IMAGE:
                heightDp = 150;
                break;
            default:
                textSizeSp = 16;
                break;
        }
    }

    public static UiComponent createDefault(UiComponentType type) {
        switch (type) {
            case HEADER:
                UiComponent header = new UiComponent(type, "Hero headline", "A short explanation for the screen", "Sand", "Cobalt", true, true, 20, 24, "start");
                header.setTextSizeSp(26);
                return header;
            case TEXT:
                UiComponent text = new UiComponent(type, "Body copy", "Explain the value in one focused sentence", "Ice", "Ink", true, false, 16, 18, "start");
                text.setTextSizeSp(17);
                return text;
            case BUTTON:
                UiComponent button = new UiComponent(type, "Continue", "", "Cobalt", "Sand", true, true, 18, 20, "center");
                button.setHeightDp(54);
                button.setTextSizeSp(15);
                return button;
            case INPUT:
                UiComponent input = new UiComponent(type, "Email address", "you@example.com", "White", "Mint", true, false, 14, 18, "start");
                input.setHeightDp(0);
                input.setTextSizeSp(16);
                return input;
            case TEXT_AREA:
                return new UiComponent(type, "Message", "Write your message here", "White", "Ink", true, false, 14, 18, "start");
            case SEARCH_BAR:
                return new UiComponent(type, "Search", "Search products", "White", "Cobalt", true, false, 14, 28, "start");
            case CARD:
                UiComponent card = new UiComponent(type, "Feature card", "Highlight one benefit or next step", "White", "Sunset", true, false, 18, 24, "start");
                card.setTextSizeSp(18);
                return card;
            case IMAGE:
                UiComponent image = new UiComponent(type, "Image placeholder", "16:9 visual area", "Charcoal", "Gold", true, false, 18, 28, "center");
                image.setHeightDp(150);
                image.setTextSizeSp(16);
                return image;
            case TABS:
                return new UiComponent(type, "Overview, Details, Activity", "Overview", "White", "Cobalt", true, true, 8, 18, "center");
            case DROPDOWN:
                return new UiComponent(type, "Country", "Select an option", "White", "Ink", true, false, 14, 18, "start");
            case CHECKBOX:
                return new UiComponent(type, "Accept terms", "Checked", "White", "Mint", true, false, 12, 16, "start");
            case RADIO_GROUP:
                return new UiComponent(type, "Plan", "Free, Pro, Team", "White", "Cobalt", true, false, 12, 16, "start");
            case SWITCH:
                return new UiComponent(type, "Enable notifications", "On", "White", "Cobalt", true, false, 12, 16, "start");
            case DIVIDER:
                return new UiComponent(type, "Section divider", "", "White", "Ink", true, false, 8, 8, "center");
            case SLIDER:
                return new UiComponent(type, "Volume", "60%", "White", "Mint", true, false, 12, 18, "start");
            case TOP_APP_BAR:
                return new UiComponent(type, "Dashboard", "Menu, Search, Profile", "Cobalt", "White", true, true, 12, 18, "start");
            case BOTTOM_NAV:
                return new UiComponent(type, "Home, Search, Profile", "Home", "White", "Cobalt", true, false, 8, 18, "center");
            case FAB:
                return new UiComponent(type, "+", "Primary action", "Mint", "White", false, true, 8, 32, "center");
            case ICON_BUTTON:
                return new UiComponent(type, "Search", "Icon action", "White", "Cobalt", false, false, 8, 20, "center");
            case AVATAR:
                return new UiComponent(type, "Andrei", "AE", "Cobalt", "White", false, true, 8, 32, "center");
            case BADGE:
                return new UiComponent(type, "Notifications", "3", "Sunset", "White", false, true, 8, 18, "center");
            case CHIP:
                return new UiComponent(type, "Filter", "Selected", "Ice", "Cobalt", false, false, 10, 20, "center");
            case LIST_ITEM:
                return new UiComponent(type, "Order #1042", "Delivered today", "White", "Ink", true, false, 12, 18, "start");
            case GRID_ITEM:
                return new UiComponent(type, "Product tile", "$24", "White", "Sunset", false, false, 14, 20, "center");
            case MENU:
                return new UiComponent(type, "Menu", "Edit, Share, Delete", "White", "Ink", false, false, 12, 16, "start");
            case DIALOG:
                return new UiComponent(type, "Delete item?", "This action can be cancelled", "White", "Cobalt", true, false, 16, 24, "start");
            case SNACKBAR:
                return new UiComponent(type, "Saved", "Undo", "Charcoal", "White", true, true, 12, 18, "start");
            case RATING:
                return new UiComponent(type, "Rating", "4", "White", "Gold", false, false, 10, 16, "center");
            case STEPPER:
                return new UiComponent(type, "Step 2 of 4", "Account details", "White", "Cobalt", true, false, 12, 18, "start");
            case LOADING:
                return new UiComponent(type, "Loading", "In progress", "White", "Mint", false, false, 8, 16, "center");
            case SKELETON:
                return new UiComponent(type, "Skeleton", "Loading state", "Ice", "Cobalt", true, false, 12, 18, "start");
            case MAP:
                return new UiComponent(type, "Map area", "Pinned location", "Ice", "Mint", true, false, 12, 20, "center");
            case CHART:
                return new UiComponent(type, "Revenue", "Q1, Q2, Q3", "White", "Cobalt", true, false, 12, 20, "start");
            case MEDIA_PLAYER:
                return new UiComponent(type, "Preview", "00:32 / 03:10", "Charcoal", "Gold", true, false, 12, 18, "start");
            case QR_CODE:
                return new UiComponent(type, "Scan code", "QR", "White", "Ink", false, false, 10, 16, "center");
            case SPACER:
                return new UiComponent(type, "Spacer", "", "White", "Ice", true, false, 8, 8, "center");
            case ROW:
                return new UiComponent(type, "Horizontal group", "Item A, Item B", "Ice", "Cobalt", true, false, 10, 16, "center");
            case COLUMN:
                return new UiComponent(type, "Vertical group", "Item A, Item B", "Ice", "Cobalt", true, false, 10, 16, "center");
            case GRID:
                return new UiComponent(type, "Grid layout", "2 x 2", "Ice", "Cobalt", true, false, 10, 16, "center");
            case PROGRESS:
            default:
                return new UiComponent(type, "Setup progress", "65%", "White", "Mint", true, false, 14, 18, "start");
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

    public int getWidthPercent() {
        return widthPercent;
    }

    public void setWidthPercent(int widthPercent) {
        this.widthPercent = Math.max(30, Math.min(100, widthPercent));
        this.widthDp = 0;
    }

    public int getWidthDp() {
        return widthDp;
    }

    public void setWidthDp(int widthDp) {
        this.widthDp = Math.max(0, widthDp);
    }

    public int getHeightDp() {
        return heightDp;
    }

    public void setHeightDp(int heightDp) {
        this.heightDp = Math.max(0, heightDp);
    }

    public int getTextSizeSp() {
        return textSizeSp;
    }

    public void setTextSizeSp(int textSizeSp) {
        this.textSizeSp = Math.max(10, Math.min(32, textSizeSp));
    }

    public int getXdp() {
        return xDp;
    }

    public void setXdp(int xDp) {
        this.xDp = Math.max(0, xDp);
    }

    public int getYdp() {
        return yDp;
    }

    public void setYdp(int yDp) {
        this.yDp = Math.max(0, yDp);
    }

    public String getSummary() {
        if (helper == null || helper.isEmpty()) {
            return type.getLabel();
        }
        return type.getLabel() + " - " + helper;
    }
}
