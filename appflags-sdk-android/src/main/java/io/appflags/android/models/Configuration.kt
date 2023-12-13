package io.appflags.android.models

import io.appflags.protos.ComputedFlag

data class Configuration (
    val flags: Map<String, ComputedFlag>
)
