package com.odin2.odinsettings;

public final class OdinControl {
    public final String id;
    public final String label;
    public final String mappingStatus;
    public final String summary;
    public final String lineageAction;
    public final String[] candidateReadPaths;

    OdinControl(String id, String label, String mappingStatus, String summary,
            String lineageAction, String[] candidateReadPaths) {
        this.id = id;
        this.label = label;
        this.mappingStatus = mappingStatus;
        this.summary = summary;
        this.lineageAction = lineageAction;
        this.candidateReadPaths = candidateReadPaths.clone();
    }
}
