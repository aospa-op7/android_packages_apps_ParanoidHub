/*
 * Copyright (C) 2017 The LineageOS Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/**
 * This class handles all updates fetched from the server/local json
 **/
public class UpdateBuilder {

    private static final String TAG = "UpdateBuilder";
    private static Update mUpdate = null;

    // This should really return an UpdateBaseInfo object, but currently this is only
    // used to initialize UpdateInfo objects
    private static UpdateInfo buildUpdate(Context context, JSONObject object) throws JSONException {
        Update update = new Update(context);
        update.setName(object.getString("filename"));
        update.setVersion(object.getString("version"));
        update.setVersionNumber(object.getString("number"));
        update.setBuildType(object.getString("romtype"));
        update.setTimestamp(object.getLong("datetime"));
        update.setFileSize(object.getLong("size"));
        update.setDownloadUrl(object.getString("url"));
        update.setDownloadId(object.getString("id"));
        return update;
    }

    private static Configuration buildConfiguration(JSONObject object) throws JSONException {
        Configuration config = new Configuration();
        config.setOtaEnabled(object.getString("enabled"));
        config.setOtaWhitelistOnly(object.getString("whitelist_only"));
        return config;
    }

    private static Changelog buildChangelog(JSONObject object) throws JSONException {
        Changelog changelog = new Changelog();
        changelog.setVersion(object.getString("version"));
        changelog.setVersionNumber(object.getString("number"));
        changelog.setBuildType(object.getString("romtype"));
        changelog.setId(object.getString("id"));
        changelog.setChangelog(object.getString("info"));
        changelog.setChangelogBrief(object.getString("info_brief"));
        return changelog;
    }

    public static UpdateInfo matchMakeJson(Context context, File file)
            throws IOException, JSONException {
        UpdateInfo update = null;
        StringBuilder json = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null;) {
                json.append(line);
            }
        }

        JSONObject obj = new JSONObject(json.toString());
        JSONArray updates = obj.getJSONArray("updates");
        for (int i = 0; i < updates.length(); i++) {
            if (updates.isNull(i)) {
                continue;
            }
            try {
                update = buildUpdate(context, updates.getJSONObject(i));
            } catch (JSONException e) {
                Log.d(TAG, "Could not parse update object, index=" + i, e);
            }
        }
        return update;
    }

    public static Configuration matchMakeConfiguration(File oldConfig, File newConfig)
            throws IOException, JSONException {
        Configuration config = null;
        StringBuilder json = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(newConfig))) {
            for (String line; (line = br.readLine()) != null;) {
                json.append(line);
            }
        }
        JSONObject obj = new JSONObject(json.toString());
        JSONArray changes = obj.getJSONArray("ota_configuration");
        for (int i = 0; i < changes.length(); i++) {
            if (changes.isNull(i)) {
                continue;
            }
            try {
                config = buildConfiguration(changes.getJSONObject(i));
            } catch (JSONException e) {
                Log.d(TAG, "Could not parse configuration object, index=" + i, e);
            }
        }
        newConfig.renameTo(oldConfig);
        return config;
    }

    public static Changelog buildChangelog(File oldChangelog, File newChangelog)
            throws IOException, JSONException {
        Changelog changelog = null;
        StringBuilder json = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(newChangelog))) {
            for (String line; (line = br.readLine()) != null;) {
                json.append(line);
            }
        }
        JSONObject obj = new JSONObject(json.toString());
        JSONArray changes = obj.getJSONArray("changelog");
        for (int i = 0; i < changes.length(); i++) {
            if (changes.isNull(i)) {
                continue;
            }
            try {
                changelog = buildChangelog(changes.getJSONObject(i));
            } catch (JSONException e) {
                Log.d(TAG, "Could not parse changelog object, index=" + i, e);
            }
        }
        newChangelog.renameTo(oldChangelog);
        return changelog;
    }

    /**
     * Compares two json formatted updates list files
     *
     * @param oldJson old update list
     * @param newJson new update list
     * @return true if old/new has at least a compatible update
     * @throws IOException
     * @throws JSONException
     */
    public static boolean isNewUpdate(Context context, File oldJson, File newJson, boolean isReadyForRollout)
            throws IOException, JSONException {
        UpdateInfo oldListInfo = matchMakeJson(context, oldJson);
        UpdateInfo newListInfo = matchMakeJson(context, newJson);
        if (oldListInfo == null || newListInfo == null) {
            return false;
        }

        Update oldUpdateList = new Update(oldListInfo);
        Update newUpdateList = new Update(newListInfo);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int status = prefs.getInt(Constants.UPDATE_STATUS, -1);
        if (status == UpdateStatus.DOWNLOADING 
                || status == UpdateStatus.DOWNLOADED) {
            Log.d(TAG, "Update is downloading or already downloaded");
            return false;
        }

        if (!isReadyForRollout) {
            Log.d(TAG, "Device is not ready for rollout");
            return false;
        }

        Version olVersion = new Version(context, oldUpdateList);
        if (olVersion.isUpdateAvailable()) {
            mUpdate = oldUpdateList;
            Log.d(TAG, "Update available via old (cached) list");
            return true;
        }

        Version nlVersion = new Version(context, newUpdateList);
        if (nlVersion.isUpdateAvailable()) {
            mUpdate = newUpdateList;
            Log.d(TAG, "Update available via new list");
            return true;
        }
        return false;
    }

    public static Update getUpdate() {
        return mUpdate;
    }
}
