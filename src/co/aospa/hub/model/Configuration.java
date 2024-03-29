/*
 * Copyright (C) 2020 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.aospa.hub.model;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import co.aospa.hub.HubActivity;
import co.aospa.hub.misc.Constants;

public class Configuration {

    private boolean mEnabled;
    private boolean mWhitelistOnly;
    private String mChangelog;
    private String mBetaChangelog;

    public Configuration() {
    }

    public void setOtaEnabled(String enabled) {
        boolean otaEnabled = Boolean.parseBoolean(enabled); 
        if (otaEnabled != mEnabled) {
            mEnabled = otaEnabled;
        }
    }

    public void setOtaWhitelistOnly(String enabled) {
        boolean whitelistOnly = Boolean.parseBoolean(enabled); 
        if (whitelistOnly != mWhitelistOnly) {
            mWhitelistOnly = whitelistOnly;
        }
    }

    public boolean isOtaEnabledFromServer() {
        return mEnabled;
    }

    public boolean isOtaWhitelistOnly() {
        return mWhitelistOnly;
    }
}
