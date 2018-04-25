package com.ota.download;

import java.net.InetSocketAddress;
import java.net.Proxy;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpPost;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

public class CheckRequestState {

	public boolean isWapState=false;

	public static final String GWAP = "3gwap";

	public static final String CMWAP = "cmwap";

	public static final String CTWAP = "ctwap";

	public static final String UNIWAP = "uniwap";

	public static final String SCHEME = "http://";

	public static final String X_ONLINE_HOST = "X-Online-Host";

	public static final String X_OFFLINE_HOST = "http://10.0.0.172";

	
	public static final String DEFAULT_PROXY_HOST="10.0.0.172";

	public static boolean isWap(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkinfo = connManager.getActiveNetworkInfo();
		String extraInfo = null;
		if (networkinfo != null) {
			extraInfo = networkinfo.getExtraInfo();
		}
		if (extraInfo != null
				&& (extraInfo.equals(CMWAP) 
						|| extraInfo.equals(UNIWAP))) {
			return true;
		} 
		return false;
			
	}
	
	
	public static HttpHost checkHttpHost(Context context){
		if (isWap(context)) {
			String h = android.net.Proxy.getHost(context);
			int port = android.net.Proxy.getPort(context);
			if (h == null || TextUtils.isEmpty(h.trim())) {
				h = CheckRequestState.DEFAULT_PROXY_HOST;
			}
			if (port == -1) {
				port = 80;
			}
			return new HttpHost(h, port);
		}
		return null;
		
	}
	

	public static java.net.Proxy checkUrlConnectionProxy(Context context){
		if (isWap(context)) {
			String h = android.net.Proxy.getHost(context);
			int port = android.net.Proxy.getPort(context);
			if (h == null || TextUtils.isEmpty(h.trim())) {
				h = CheckRequestState.DEFAULT_PROXY_HOST;
			}
			if (port == -1) {
				port = 80;
			}
			return new Proxy(java.net.Proxy.Type.HTTP,
					new InetSocketAddress(h, port));
		}
		return null;
	}
}
