package com.uiforge.app.model;

public enum UiComponentType {
    HEADER("Header"),
    TEXT("Text"),
    BUTTON("Button"),
    INPUT("Input"),
    TEXT_AREA("Text area"),
    SEARCH_BAR("Search bar"),
    CARD("Card"),
    IMAGE("Image"),
    TABS("Tabs"),
    DROPDOWN("Dropdown"),
    CHECKBOX("Checkbox"),
    RADIO_GROUP("Radio group"),
    SWITCH("Switch"),
    DIVIDER("Divider"),
    PROGRESS("Progress"),
    SLIDER("Slider"),
    TOP_APP_BAR("Top app bar"),
    BOTTOM_NAV("Bottom nav"),
    FAB("FAB"),
    ICON_BUTTON("Icon button"),
    AVATAR("Avatar"),
    BADGE("Badge"),
    CHIP("Chip"),
    LIST_ITEM("List item"),
    GRID_ITEM("Grid item"),
    MENU("Menu"),
    DIALOG("Dialog"),
    SNACKBAR("Snackbar"),
    RATING("Rating"),
    STEPPER("Stepper"),
    LOADING("Loading"),
    SKELETON("Skeleton"),
    MAP("Map"),
    CHART("Chart"),
    MEDIA_PLAYER("Media player"),
    QR_CODE("QR code"),
    SPACER("Spacer"),
    ROW("Row"),
    COLUMN("Column"),
    GRID("Grid");

    private final String label;

    UiComponentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
