package com.pooyan.fxcalc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        try { Sched.restore(c); } catch (Exception e) { }
        try { CalReceiver.schedule(c); } catch (Exception e) { }
        try { PriceService.sync(c); } catch (Exception e) { }
    }
}
