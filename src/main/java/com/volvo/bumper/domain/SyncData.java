package com.volvo.bumper.domain;

import java.util.List;

public record SyncData(
    List<Repository> repos,
    List<IgnoredRepository> ignored,
    SyncResult result,
    List<String> logs) {}
