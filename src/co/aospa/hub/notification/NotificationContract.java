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
package co.aospa.hub.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import co.aospa.hub.HubActivity;
import co.aospa.hub.R;

public class NotificationContract {

    public static final String TAG = "NotificationContract";

    private final Context mContext;
    private final NotificationCompat.Builder mBuilder;

    public NotificationContract(Context context, NotificationManager manager, String channel, boolean highImportance) {
        mContext = context;
        NotificationChannel notificationChannel = new NotificationChannel(
                channel,
                getChannel(channel),
                highImportance ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(notificationChannel);

        mBuilder = new NotificationCompat.Builder(context, channel);
        mBuilder.clearActions();
        mBuilder.setColor(context.getResources().getColor(R.color.theme_accent));
        mBuilder.setShowWhen(false);

        Intent hub = new Intent(context, HubActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, hub,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mBuilder.setContentIntent(intent);
    }

    public Notification write() {
        return mBuilder.build();
    }
        

    public void setTitle(String title) {
        mBuilder.setContentTitle(title);
    }

    public void setText(String text) {
        mBuilder.setContentText(text);
    }

    public void setTextStyle(String text) {
        mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
    }

    public void setProgress(int percent, int progress, boolean intermediate) {
        mBuilder.setProgress(percent, progress, intermediate);
    }

    public void setIcon(int icon) {
        mBuilder.setSmallIcon(icon);
    }

    public void setColor(int color) {
        mBuilder.setColor(ContextCompat.getColor(mContext, color));
    }

    public void setDismissible(boolean isDimissible) {
        mBuilder.setOngoing(!isDimissible);
        mBuilder.setAutoCancel(isDimissible);
    }

    public void setIntent(PendingIntent intent) {
        mBuilder.setContentIntent(intent);
    }

    public void setExtras(Bundle extras) {
        mBuilder.setExtras(extras);
    }

    public Bundle getExtras() {
        return mBuilder.getExtras();
    }

    private String getChannel(String channel) {
        String currentChannel = "";
        switch (channel) {
            case NotificationContractor.INSTALL_ERROR_NOTIFICATION_CHANNEL:
                currentChannel = mContext.getResources().getString(R.string.channel_update_failed_title);
                break;
            case NotificationContractor.ONGOING_NOTIFICATION_CHANNEL:
                currentChannel = mContext.getResources().getString(R.string.channel_ongoing_title);
                break;
            case NotificationContractor.PROGRESS_NOTIFICATION_CHANNEL:
                currentChannel = mContext.getResources().getString(R.string.channel_progress_title);
                break;
            case NotificationContractor.NEW_UPDATES_NOTIFICATION_CHANNEL:
                currentChannel = mContext.getResources().getString(R.string.channel_new_updates_title);
                break;
        }
        return currentChannel;
    }
}
