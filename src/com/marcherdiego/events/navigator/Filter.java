package com.marcherdiego.events.navigator;

import com.intellij.usages.Usage;

public interface Filter {
    boolean shouldShow(Usage usage);
}
