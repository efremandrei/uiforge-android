package com.uiforge.app.model;

public enum UiComponentType {
    HEADER("Header"),
    TEXT("Text"),
    BUTTON("Button"),
    INPUT("Input"),
    CARD("Card"),
    IMAGE("Image");

    private final String label;

    UiComponentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
