package com.hushgate.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DigestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Digest.post(context);
    }
}
