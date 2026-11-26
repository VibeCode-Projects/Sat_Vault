package com.satvault.model;

public enum DocumentCategory {
    ACADEMIC("Academic"),
    CERTIFICATES("Certificates"),
    IDENTITY("Identity"),
    FINANCIAL("Financial"),
    MEDICAL("Medical"),
    OTHER("Other");

    private final String label;

    DocumentCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
