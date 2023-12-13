package io.appflags.android.utils

import io.appflags.protos.PlatformData
import io.appflags.android.BuildConfig;

class PlatformUtil private constructor() {
    companion object {
        fun getPlatformData(): PlatformData {
            var platformData = PlatformData.newBuilder()
                .setSdk("Android")
                .setSdkType("mobile")
                .setSdkVersion(BuildConfig.SDK_VERSION)
                .setPlatform("Android")
                .setPlatformVersion(android.os.Build.VERSION.SDK_INT.toString())
                .build();
            return platformData;
        }
    }
}

