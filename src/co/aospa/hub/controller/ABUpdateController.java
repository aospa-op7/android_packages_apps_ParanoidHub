/*
 * Copyright (C) 2017 The LineageOS Project
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
package co.aospa.hub.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import androidx.preference.PreferenceManager;

import co.aospa.hub.HubController;
import co.aospa.hub.R;
import co.aospa.hub.misc.Constants;
import co.aospa.hub.misc.Utils;
import co.aospa.hub.model.Update;
import co.aospa.hub.model.UpdateStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ABUpdateController extends UpdateEngineCallback {

    private static final String TAG = "ABUpdateController";

    private static ABUpdateController sInstance = null;

    private final HubController mController;
    private final Context mContext;
    private String mDownloadId;

    private final UpdateEngine mUpdateEngine;
    private boolean mBound;

    private boolean mFinalizing;
    private int mProgress;

    private ABUpdateController(Context context, HubController controller) {
        mController = controller;
        mContext = context.getApplicationContext();
        mUpdateEngine = new UpdateEngine();
    }

    public static synchronized ABUpdateController getInstance(Context context,
            HubController controller) {
        if (sInstance == null) {
            sInstance = new ABUpdateController(context, controller);
        }
        return sInstance;
    }

    public void install(String downloadId) {
        if (isInstalling(mContext)) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        mDownloadId = downloadId;

        File file = mController.getActualUpdate(mDownloadId).getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            mController.getActualUpdate(downloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            mController.notifyUpdateStatusChanged(mController.getActualUpdate(downloadId), HubController.STATE_STATUS_CHANGED);
            return;
        }

        long offset;
        String[] headerKeyValuePairs;
        try {
            ZipFile zipFile = new ZipFile(file);
            offset = Utils.getZipEntryOffset(zipFile, Constants.AB_PAYLOAD_BIN_PATH);
            ZipEntry payloadPropEntry = zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH);
            try (InputStream is = zipFile.getInputStream(payloadPropEntry);
                 InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader br = new BufferedReader(isr)) {
                List<String> lines = new ArrayList<>();
                for (String line; (line = br.readLine()) != null;) {
                    lines.add(line);
                }
                headerKeyValuePairs = new String[lines.size()];
                headerKeyValuePairs = lines.toArray(headerKeyValuePairs);
            }
            zipFile.close();
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Could not prepare " + file, e);
            mController.getActualUpdate(mDownloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);
            return;
        }

        if (!mBound) {
            mBound = mUpdateEngine.bind(this);
            if (!mBound) {
                Log.e(TAG, "Could not bind");
                mController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
                mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);
                return;
            }
        }

        SharedPreferences prefs = mContext.getSharedPreferences(Utils.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        boolean enableABPerfModeDefault = mContext.getResources().getBoolean(R.bool.config_abPerformanceModeDefault);
        boolean enableABPerfMode = prefs.getBoolean(Constants.PREF_AB_PERF_MODE, enableABPerfModeDefault);
        mUpdateEngine.setPerformanceMode(enableABPerfMode);

        String zipFileUri = "file://" + file.getAbsolutePath();
        mUpdateEngine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);

        mController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);

        setDownloadId(mDownloadId, false);
        setInstalling(true);

    }

    public void suspend() {
        if (!isInstalling(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return;
        }

        mUpdateEngine.suspend();
        mController.getActualUpdate(mDownloadId)
                .setStatus(UpdateStatus.INSTALLATION_SUSPENDED, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);
        setSuspended(true);

    }

    public void resume() {
        if (!isInstallSuspended(mContext)) {
            Log.e(TAG, "cancel: No update is suspended");
            return;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return;
        }

        mUpdateEngine.resume();

        mController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);
        mController.getActualUpdate(mDownloadId).setInstallProgress(mProgress);
        mController.getActualUpdate(mDownloadId).setFinalizing(mFinalizing);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_INSTALL_PROGRESS);

        setSuspended(false);

    }

    public boolean cancel() {
        if (!isInstalling(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mUpdateEngine.cancel();
        setFinished(false);

        mController.getActualUpdate(mDownloadId)
                .setStatus(UpdateStatus.INSTALLATION_CANCELLED, mContext);
        mController.notifyUpdateStatusChanged(mController.getActualUpdate(mDownloadId), HubController.STATE_STATUS_CHANGED);

        return true;
    }

    public void reconnect() {
        if (!isInstalling(mContext)) {
            Log.e(TAG, "reconnect: Not installing any update");
            return;
        }

        if (mBound) {
            return;
        }

        mDownloadId = getDownloadId();

        // We will get a status notification as soon as we are connected
        mBound = mUpdateEngine.bind(this);
        if (!mBound) {
            Log.e(TAG, "Could not bind");
        }

    }

    private void setInstalling(boolean installing) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putBoolean(Constants.IS_INSTALLING_AB, installing).apply();
    }

    private void setSuspended(boolean suspended) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putBoolean(Constants.IS_INSTALL_SUSPENDED, suspended).apply();
        setInstalling(!suspended);
    }

    private void setFinished(boolean finished) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        prefs.edit().putBoolean(Constants.NEEDS_REBOOT_AFTER_UPDATE, finished).apply();
        setDownloadId(mDownloadId, true);
    }

    private void setDownloadId(String id, boolean remove) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (remove) {
            prefs.edit().remove(Constants.DOWNLOAD_ID_AB).apply();
            return;
        }
        prefs.edit().putString(Constants.DOWNLOAD_ID_AB, id).apply();
    }

    private String getDownloadId() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getString(Constants.DOWNLOAD_ID_AB, null);
    }

    public static synchronized boolean isInstalling(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.IS_INSTALLING_AB, false);
    }

    public static synchronized boolean isInstallSuspended(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(Constants.IS_INSTALL_SUSPENDED, false);
    }

    @Override
    public void onStatusUpdate(int status, float percent) {
        Update update = mController.getActualUpdate(mDownloadId);
        if (update == null) {
            // We read the id from a preference, the update could no longer exist
            setFinished(status == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT);
            return;
        }

        switch (status) {
            case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
            case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                if (update.getStatus() != UpdateStatus.INSTALLING) {
                    update.setStatus(UpdateStatus.INSTALLING, mContext);
                    mController.notifyUpdateStatusChanged(update, HubController.STATE_STATUS_CHANGED);
                }
                mProgress = Math.round(percent * 100);
                mController.getActualUpdate(mDownloadId).setInstallProgress(mProgress);
                mFinalizing = status == UpdateEngine.UpdateStatusConstants.FINALIZING;
                mController.getActualUpdate(mDownloadId).setFinalizing(mFinalizing);
                mController.notifyUpdateStatusChanged(update, HubController.STATE_INSTALL_PROGRESS);
            }
            break;
            case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT: {
                setInstalling(false);
                setFinished(true);
                update.setInstallProgress(0);
                update.setStatus(UpdateStatus.INSTALLED, mContext);
                mController.notifyUpdateStatusChanged(update, HubController.STATE_STATUS_CHANGED);
            }
            break;
            case UpdateEngine.UpdateStatusConstants.IDLE: {
                // The service was restarted because we thought we were installing an
                // update, but we aren't, so clear everything.
                setFinished(false);
            }
            break;
        }
    }

    @Override
    public void onPayloadApplicationComplete(int errorCode) {
        if (errorCode != UpdateEngine.ErrorCodeConstants.SUCCESS) {
            setFinished(false);
            Update update = mController.getActualUpdate(mDownloadId);
            update.setInstallProgress(0);
            update.setStatus(UpdateStatus.INSTALLATION_FAILED, mContext);
            mController.notifyUpdateStatusChanged(update, HubController.STATE_STATUS_CHANGED);
        }
    }
}
