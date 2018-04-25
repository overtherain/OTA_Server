package com.ota.download;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.telephony.TelephonyManager;

public class CGNetWorkUtils {
	public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null)
            return networkInfo.isConnectedOrConnecting();
        return false;
    }
	
	public static boolean isNetWorkWifi(Context context) {
		ConnectivityManager conManager = 
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if(conManager !=null){
			NetworkInfo ni = conManager.getActiveNetworkInfo(); 
			if(ni !=null){				
				 return ni.getType() == ConnectivityManager.TYPE_WIFI;
			}
		}		
       return false;
	}
	
}
