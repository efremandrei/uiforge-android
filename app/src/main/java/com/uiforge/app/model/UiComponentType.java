package com.uiforge.app.model;

public enum UiComponentType {
    HEADER("Header"),
    TEXT("Text"),
    BUTTON("Button"),
    INPUT("Input"),
    CARD("Card"),
    IMAGE("Image"),
    TABS("Tabs"),
    DROPDOWN("Dropdown"),
    CHECKBOX("Checkbox"),
    SWITCH("Switch"),
    DIVIDER("Divider"),
    PROGRESS("Progress");

    private final String label;

    UiComponentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
