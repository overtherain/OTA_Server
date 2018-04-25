package com.otarecovery;

import java.io.File;
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RecoverySystem;
import android.util.Log;

public class RecoveryReceiver  extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        String path = intent.getStringExtra("filePath");
        
        Log.d("OTAUpdatesActivity","RecoveryReceiver,path = " + path);

        File packageFile = new File(path);
        try {
    		RecoverySystem.installPackage(context, packageFile);
        } catch (IOException e) {
        	e.printStackTrace();
        }
    }
}