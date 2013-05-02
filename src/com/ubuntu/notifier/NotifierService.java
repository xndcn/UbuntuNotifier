package com.ubuntu.notifier;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Debug;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RemoteViews;

public class NotifierService extends AccessibilityService {
	PackageManager pkgManger;
	
	static final Pattern notificationPattern = Pattern.compile("    NotificationRecord\\{(\\p{XDigit}+) pkg=([\\p{Alnum}.]+) id=(\\p{XDigit}+) tag=(\\p{ASCII}+)\\}");
	static final int textId = Resources.getSystem().getIdentifier("text", "id", "android");
	
	List<Notification> notifications = new ArrayList<Notification>();
	
	protected String getAppName(String pkgName) {
		String name = null;		
		try {
			ApplicationInfo appInfo = pkgManger.getApplicationInfo(pkgName, PackageManager.GET_META_DATA);
			Object label = appInfo.loadLabel(pkgManger);
			if (label != null) {
				name = label.toString();
			}
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return name;
	}
	
	protected Bitmap getIcon(String pkgName, int iconId) {
		Bitmap icon = null;
		Resources res;
		try {
			res = pkgManger.getResourcesForApplication(pkgName);
			icon = BitmapFactory.decodeResource(res, iconId);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return icon;
	}
	
	protected Notification getNotification(AccessibilityEvent event) {
		Notification notify = new Notification();
		
		notify.pkgName = event.getPackageName().toString();
		
		Parcelable parcel = event.getParcelableData();
		if (parcel instanceof android.app.Notification) {
			Map<Integer, String> textMap = new HashMap<Integer, String>();
			android.app.Notification notification = (android.app.Notification) parcel;
			RemoteViews view = notification.contentView;
			try {					
				Field mActions = view.getClass().getDeclaredField("mActions");
				mActions.setAccessible(true);
				ArrayList<Object> actions = (ArrayList<Object>) mActions.get(view);
				for (Object action : actions) {
					Class reflectionAction = action.getClass();					
					
					if ("ReflectionAction".equals(reflectionAction.getSimpleName())) {
						Field viewId = reflectionAction.getDeclaredField("viewId");
						Field value = reflectionAction.getDeclaredField("value");
						Field methodName = reflectionAction.getDeclaredField("methodName");						
						viewId.setAccessible(true);
						value.setAccessible(true);						
						methodName.setAccessible(true);
						
						//Log.d("aa", "remote:" + methodName.get(action).toString() + ", " + viewId.getInt(action) + ", " + value.get(action).toString());
						
						if ("setText".equals(methodName.get(action).toString())) {
							textMap.put(viewId.getInt(action), value.get(action).toString());
						}
					}
				}
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			
			if (notification.tickerText != null) {
				notify.ticker = notification.tickerText.toString();
			}
			
			notify.name = getAppName(notify.pkgName);
			notify.title = textMap.get(android.R.id.title);				
			notify.text = textMap.get(textId);
			notify.number = notification.number;
			notify.icon = getIcon(notify.pkgName, notification.icon);
		}
		
		return notify;
	}	

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		
		if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
			
			Log.d("aa", "onAccessibilityEvent");
			
			Notification notify = getNotification(event);
			
			JSONObject json = notify.getJSON();
						
			
			Log.d("aa", String.valueOf(notify.number));
			Log.d("aa", String.valueOf(json.toString().length()));
			//Log.d("aa", json.toString());
			
			
			if (ConnectService.connectOSD()) {
				ConnectService.sendOSD(json.toString());
				ConnectService.closeOSD();
			}			
			
			try {
				Debug.dumpService(NOTIFICATION_SERVICE, new FileOutputStream("/cache/statusbar.txt").getFD(), null);
				BufferedReader reader = new BufferedReader(new FileReader("/cache/statusbar.txt"));
				String line = null;
				boolean started = false;
				List<Notification> newNotifications = new ArrayList<Notification>(notifications.size());
				
				while ((line = reader.readLine()) != null) {
					if (started) {
						if ("  ".equals(line)) {
							break;
						}
					
						Matcher matcher = notificationPattern.matcher(line);
						
						if (matcher.find()) {
							String pkgName = matcher.group(2);
							int id = Integer.parseInt(matcher.group(3), 16);
							String tag = matcher.group(4);
							
							boolean findout = false;							
							for (Notification anotify : notifications) {
								if (pkgName.equals(anotify.pkgName) &&
										anotify.id == id) {
									newNotifications.add(anotify);
									findout = true;
									break;
								}
							}
							if (!findout) {
								if (pkgName.equals(notify.pkgName)) {
									notify.id = id;
									notify.tag = tag;
									newNotifications.add(notify);
								}
							}
						}
					} else if ("  Notification List:".equals(line)){
						started = true;
					}
				}
				notifications = newNotifications;
				reader.close();				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (ConnectService.connectMenu()) {
				JSONArray array = new JSONArray();
				for (Notification anotify : notifications) {
					JSONObject ajson = anotify.getJSON();		
					array.put(ajson);
				}
				ConnectService.sendMenu(array.toString());
				ConnectService.closeMenu();
			}			
		}
	}

	@Override
	protected void onServiceConnected() {
		AccessibilityServiceInfo info = new AccessibilityServiceInfo();
		info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED | AccessibilityEvent.TYPE_VIEW_CLICKED;
		info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
		info.notificationTimeout = 10;
		setServiceInfo(info);
		
		Log.d("aa", "onServiceConnected");		
		pkgManger = getPackageManager();
	}

	@Override
	public void onInterrupt() {
		// TODO Auto-generated method stub
		
	}
}

class Notification {
	String pkgName;
	String name;
	int id;
	String tag;
	int number;
	String ticker;
	String title;
	String text;
	Bitmap icon;
	
	public JSONObject getJSON() {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();		
		JSONObject json = new JSONObject();
		try {
			json.put("package", pkgName);
			json.put("id", id);
			json.put("tag", tag);
			json.put("number", number);
			json.put("name", name);
			json.put("ticker", ticker);
			json.put("title", title);
			json.put("text", text);
			if (icon != null) {
				icon.compress(CompressFormat.PNG, 100, buffer);
				json.put("icon", Base64.encodeToString(buffer.toByteArray(), Base64.DEFAULT));
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return json;
	}
}
