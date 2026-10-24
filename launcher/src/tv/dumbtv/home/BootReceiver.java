package tv.dumbtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the home keeper back after a reboot or an update. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        android.util.Log.i("dumbtvtest", "keeper: boot receiver " + intent.getAction());
        Keeper.start(context);
    }
}
