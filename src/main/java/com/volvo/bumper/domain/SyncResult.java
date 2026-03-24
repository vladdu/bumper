package com.volvo.bumper.domain;

import java.util.List;

public record SyncResult(
    int updated,
    int total,
    int newlyIgnored,
    int totalIgnored,
    List<String> newRegularNames,
    List<String> newIgnoredNames,
    List<String> forgeErrors) {}
