/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.silentphone2.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.userinfo.LoadUserInfo;

/**
 *
 * This is a simple content provider that returns a 1 row cursor that holds the
 * status code of SilentPhone.
 *
 * For BlackPhone this provider also contains an 'insert' function to receive some
 * data from the BlackPhone configuration application.
 *
 * Created by werner on 04.06.13.
 */
public class StatusProvider extends ContentProvider {

    private static final String TAG = "StatusProvider";

    public static final String BP_FEATURECODE = "FEATURECODE";

    private static final int STATUS = 1;
    private static final int BLACK_PHONE_STATUS = 2;
    private static final int BLACK_PHONE_PROVISION = 3;

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID;

    /**
     * The content:// style URL for this provider
     */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * The MIME type of a {@link #CONTENT_URI} sub-directory of a status.
     */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/" + AUTHORITY + ".status";

    /**
     * The MIME type of a {@link #CONTENT_URI} sub-directory of setup query.
     * NOTE: this should be the same for every application that supports this query, thus it may go
     * into a more common place?
     */
    public static final String BP_CONTENT_ITEM_TYPE = "vnd.android.cursor.item/" + AUTHORITY + ".bpw-status";

    /**
     * User did not start the SilentPhone application.
     */
    private static final int NOT_STARTED = 1;

    /**
     * User started SilentPhone but provisioning is not yet complete
     */
    private static final int NOT_PROVISIONED = 2;

    /**
     * SilentPhone is offline and not registered with SilentCircle's SIP servers.
     * This is the case if the user selected 'Go offline' from the menu or if no
     * network is available.
     */
    private static final int OFFLINE = 3;

    /**
     * SilentPhone is ready.
     */
    private static final int ONLINE = 4;

    /**
     * SilentPhone currently registers with SilentCircle's SIP servers.
     * This is a transient state that may stay for a few seconds.
     */
    private static final int REGISTER = 5;

    /**
     * Status codes for BlackPhone status query.
     *
     * Documentation: Android Setup Wizard Design, by Mike Kershaw, BlackPhone
     */
    public static final int BP_NOT_PROVISIONED = 0;
    public static final int BP_PROVISIONED = 1;
    public static final int BP_EXPIRED = 2;
    public static final int BP_ERROR = 100;

    /**
     * Cannot determine status. This is an error condition.
     */
    private static final int UNKNOWN_ERROR = -1;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURIMatcher.addURI(AUTHORITY, "status", STATUS);
        sURIMatcher.addURI(AUTHORITY, "bpw-status", BLACK_PHONE_STATUS);
        sURIMatcher.addURI(AUTHORITY, "bpw-provision", BLACK_PHONE_PROVISION);
    }

    public StatusProvider() {
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int status = getCurrentStatus();

        int match = sURIMatcher.match(uri);
        switch (match) {
            case STATUS: {
                final MatrixCursor c = new MatrixCursor(new String[]{"status", "version"}, 1);
                final MatrixCursor.RowBuilder row = c.newRow();
                row.add(status);
                row.add(2);
                return c;
            }
            case BLACK_PHONE_STATUS: {
                final MatrixCursor c = new MatrixCursor(new String[]{"PROVISIONSTATUS", "STATUSTEXT", "FEATURETEXT"}, 1);
                final MatrixCursor.RowBuilder row = c.newRow();
                int bp_status = BP_PROVISIONED;
                String statusText = null;
                int valid = LoadUserInfo.checkIfExpired();

                Context ctx = getContext();
                if (ctx == null)
                    return null;
                if (valid == LoadUserInfo.VALID) {
                    bp_status = BP_EXPIRED;
                    statusText = ctx.getString(R.string.subscription_expired);
                }
                switch(status) {
                    case NOT_STARTED:
                        bp_status = BP_ERROR;
                        statusText = ctx.getString(R.string.bp_not_started);
                        break;
                    case NOT_PROVISIONED:
                        bp_status = BP_NOT_PROVISIONED;
                        break;
                    case OFFLINE:
                        statusText = ctx.getString(R.string.bp_is_offline);
                        break;
                    case ONLINE:
                        statusText = ctx.getString(R.string.bp_is_online);
                        break;
                    case REGISTER:
                        statusText = ctx.getString(R.string.bp_registering);
                        break;
                    default:
                        bp_status = BP_ERROR;
                        statusText = ctx.getString(R.string.bp_status_unknown);
                        break;
                }
                row.add(bp_status);
                row.add(statusText);
                row.add(null); // Or: row.add("feature text");
                return c;
            }
        }
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int match = sURIMatcher.match(uri);

        if (match != BLACK_PHONE_PROVISION)
            throw new UnsupportedOperationException("Cannot insert URL: " + uri);

        if (!values.containsKey(BP_FEATURECODE))
            throw new UnsupportedOperationException("Missing field, Cannot insert URL: " + uri);

        // Check if already provisioned. If yes -> don't store it, return null
        Context ctx = getContext();
        if (ctx == null)
            return null;

        SharedPreferences prefs = ctx.getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
        boolean isProvisioned = prefs.getBoolean(ProvisioningActivity.PROVISIONING_DONE, false);

        if (isProvisioned)
            return null;

        prefs.edit().putString(BP_FEATURECODE, values.getAsString(BP_FEATURECODE)).apply();

        if (ConfigurationUtilities.mTrace)
            Log.d(TAG, "Featurecode: " + values.getAsString(BP_FEATURECODE));

        return uri;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot update URL: " + uri);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Cannot delete that URL: " + uri);
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);
        switch (match) {
            case STATUS:
                return CONTENT_ITEM_TYPE;
            case BLACK_PHONE_STATUS:
                return BP_CONTENT_ITEM_TYPE;
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private int getCurrentStatus() {
        if (!TiviPhoneService.isInitialized())
            return NOT_STARTED;

        if (TiviPhoneService.phoneService == null && TiviPhoneService.doCmd("isProv") == 0)
            return NOT_PROVISIONED;

        int i = TiviPhoneService.getPhoneState();
        switch(i) {
            case 0:
                return OFFLINE;
            case 1:
                return REGISTER;
            case 2:
                return ONLINE;
            default:
                break;
        }
        return UNKNOWN_ERROR;
    }
}
