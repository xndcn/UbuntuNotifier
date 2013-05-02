package com.ubuntu.notifier;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.widget.RemoteViews;

public class StatusBarService extends Service {
	static Class IStatusBar;
	static Class StatusBarIconList;	
	static Class BaseStatusBar;
	static Class PhoneStatusBar;
	static Class CommandQueue;
	static Class Callbacks;
	static Class SystemUI;
	static Class ServiceManager;
	static Class IWindowManagerStub;
	static Class WindowManagerImpl;
	
	static Constructor CommandQueueConstructor;
	
	static Field mBarService;
	
	static Method start;
	static Method registerStatusBar;
	static Method makeStatusBarView;
	static Method getService;
	
	static Method addNotificationViews;
	
	static Context context;
	
	Object statusbar;		
	final Map<IBinder, StatusBarNotification> notifications = new HashMap<IBinder, StatusBarNotification>();
	
	static {
		try {
			IStatusBar = Class.forName("com.android.internal.statusbar.IStatusBar");
			StatusBarIconList = Class.forName("com.android.internal.statusbar.StatusBarIconList");
			ServiceManager = Class.forName("android.os.ServiceManager");			
			getService = ServiceManager.getDeclaredMethod("getService", String.class);
			
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	public static void setContext(Context ctx) {
		try {
			context = ctx;
			
			ClassLoader loader = context.getClassLoader();
			CommandQueue = loader.loadClass("com.android.systemui.statusbar.CommandQueue");
			Callbacks = CommandQueue.getClasses()[0];
			CommandQueueConstructor = CommandQueue.getDeclaredConstructors()[0];
			
			BaseStatusBar = loader.loadClass("com.android.systemui.statusbar.BaseStatusBar");	
			PhoneStatusBar = loader.loadClass("com.android.systemui.statusbar.phone.PhoneStatusBar");
			SystemUI = loader.loadClass("com.android.systemui.SystemUI");
			start = PhoneStatusBar.getDeclaredMethod("start", null);			
			
			mBarService = BaseStatusBar.getDeclaredField("mBarService");
			mBarService.setAccessible(true);
			
			makeStatusBarView = PhoneStatusBar.getDeclaredMethod("makeStatusBarView", null);
			makeStatusBarView.setAccessible(true);
			
			registerStatusBar = mBarService.getType().getDeclaredMethod("registerStatusBar", 
					IStatusBar, StatusBarIconList, List.class, List.class, int[].class, List.class);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	

	@Override
	public void onCreate() {		
		super.onCreate();
		
		StatusBarNotification.setPackageManager(getPackageManager());
		try {
			statusbar = PhoneStatusBar.newInstance();
			
			final Field mContext = SystemUI.getDeclaredField("mContext");
			mContext.set(statusbar, context);			
			start.invoke(statusbar, null);
			
			final List<Object> mNotificationKeys = new ArrayList<Object>();
			final List<Object> mNotifications = new ArrayList<Object>();			
			final Object iconList = StatusBarIconList.newInstance();
			
			final Object callbacks = Proxy.newProxyInstance(Callbacks.getClassLoader(), new Class[] { Callbacks }, new InvocationHandler() {
				@Override
				public Object invoke(Object proxy, Method method, Object[] args)
						throws Throwable {
					String name = method.getName();
					
					Object ret = method.invoke(statusbar, args);
					
					if ("addNotification".equals(name)) {
						IBinder key = (IBinder) args[0];
						StatusBarNotification notification = new StatusBarNotification("add", args[1]);
						notifications.put(key, notification);
						
						new ConnectTask().execute(notification.toJSON(key.hashCode(), true).toString());
					} else if ("updateNotification".equals(name)) {
						IBinder key = (IBinder) args[0];
						StatusBarNotification notification = new StatusBarNotification("update", args[1]);
						notifications.put(key, notification);
						
						new ConnectTask().execute(notification.toJSON(key.hashCode(), true).toString());
					} else if ("removeNotification".equals(name)) {
						IBinder key = (IBinder) args[0];
						StatusBarNotification notification = notifications.remove(key);
						notification.action = "remove";
						
						new ConnectTask().execute(notification.toJSON(key.hashCode(), true).toString());
					}
					
					return null;
				}
			});
			
			Object mbarservice = getSystemService("statusbar");
			Class StatusBarManager = mbarservice.getClass();
			Method getService = StatusBarManager.getDeclaredMethod("getService", null);
			getService.setAccessible(true);
			
			int[] switches = new int[7];
			List<IBinder> binders = new ArrayList<IBinder>();
			
			final Object commandQueue = CommandQueueConstructor.newInstance(callbacks, iconList);			
			registerStatusBar.invoke(getService.invoke(mbarservice, null), commandQueue, iconList, mNotificationKeys, mNotifications, switches, binders);
			for (int i = 0; i < mNotificationKeys.size(); i++) {
				IBinder key = (IBinder) mNotificationKeys.get(i);
				StatusBarNotification notification = new StatusBarNotification("add", mNotifications.get(i));
				notifications.put(key, notification);
				
				new ConnectTask().execute(notification.toJSON(key.hashCode(), true).toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class ConnectTask extends AsyncTask<String, Void, Void> {

	@Override
	protected Void doInBackground(String... params) {
		if (ConnectService.connectOSD()) {
			ConnectService.sendOSD(params[0]);
			ConnectService.closeOSD();
		}
		return null;
	}
	
}


class StatusBarNotification {
	static Class StatusBarNotification;
	static Field pkgField;
	static Field idField;
	static Field tagField;
	static Field notificationField;
	static Field mActions;
	
	static final int textId = Resources.getSystem().getIdentifier("text", "id", "android");
	
	static PackageManager pkgManger;
	
	static {		
		try {
			StatusBarNotification = Class.forName("com.android.internal.statusbar.StatusBarNotification");
			pkgField = StatusBarNotification.getDeclaredField("pkg");
			idField = StatusBarNotification.getDeclaredField("id");
			tagField = StatusBarNotification.getDeclaredField("tag");
			notificationField = StatusBarNotification.getDeclaredField("notification");
			
			mActions = RemoteViews.class.getDeclaredField("mActions");
			mActions.setAccessible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static public void setPackageManager(PackageManager manger) {
		pkgManger = manger;
	}
	
	String pkg;
	int id;
	String tag;
	Notification notification;
	
	String name;
	int number;
	String ticker;
	String title;
	String text;
	Bitmap icon;
	int iconId;
	
	String action;
	
	public StatusBarNotification(String action, Object obj) {
		try {
			this.action = action;
			
			pkg = (String) pkgField.get(obj);
			id = idField.getInt(obj);
			tag = (String) tagField.get(obj);
			notification = (Notification) notificationField.get(obj);
			parseNotification(notification);
			
			name = getAppName(pkg);			
			icon = getIcon(pkg, iconId);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
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
	
	protected void parseNotification(Notification notification) {
		Map<Integer, String> textMap = new HashMap<Integer, String>();
		RemoteViews view = notification.contentView;
		try {
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
					
					if ("setText".equals(methodName.get(action).toString())) {
						textMap.put(viewId.getInt(action), value.get(action).toString());
					}
				}
			}
			
			if (notification.tickerText != null) {
				ticker = notification.tickerText.toString();
			}			
			title = textMap.get(android.R.id.title);				
			text = textMap.get(textId);
			number = notification.number;
			iconId = notification.icon;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public JSONObject toJSON(int key, boolean hasIcon) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		JSONObject json = new JSONObject();
		try {
			json.put("action", action);
			json.put("key", key);
			
			JSONObject notify = new JSONObject();			
			notify.put("package", pkg);
			notify.put("id", id);
			notify.put("tag", tag);
			notify.put("number", number);
			notify.put("name", name);
			notify.put("ticker", ticker);
			notify.put("title", title);
			notify.put("text", text);
			notify.put("iconId", iconId);
			if (hasIcon && icon != null) {
				icon.compress(CompressFormat.PNG, 100, buffer);
				notify.put("icon", Base64.encodeToString(buffer.toByteArray(), Base64.DEFAULT));
			}
			
			json.put("notification", notify);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return json;
	}
}

/*
class StatusBarNotification {
	static Class StatusBarNotification;
	static Field pkgField;
	static Field idField;
	static Field tagField;
	static Field notificationField;
	static Field mActions;
	
	static final int textId = Resources.getSystem().getIdentifier("text", "id", "android");
	
	static PackageManager pkgManger;
	
	static {		
		try {
			StatusBarNotification = Class.forName("com.android.internal.statusbar.StatusBarNotification");
			pkgField = StatusBarNotification.getDeclaredField("pkg");
			idField = StatusBarNotification.getDeclaredField("id");
			tagField = StatusBarNotification.getDeclaredField("tag");
			notificationField = StatusBarNotification.getDeclaredField("notification");
			
			mActions = RemoteViews.class.getDeclaredField("mActions");
			mActions.setAccessible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static public void setPackageManager(PackageManager manger) {
		pkgManger = manger;
	}
	
	String pkg;
	int id;
	String tag;
	Notification notification;
	
	String name;
	int number;
	String ticker;
	String title;
	String text;
	Bitmap icon;
	int iconId;
	
	String action;
	
	public StatusBarNotification(String action, Object obj) {
		try {
			this.action = action;
			
			pkg = (String) pkgField.get(obj);
			id = idField.getInt(obj);
			tag = (String) tagField.get(obj);
			notification = (Notification) notificationField.get(obj);
			parseNotification(notification);
			
			name = getAppName(pkg);			
			icon = getIcon(pkg, iconId);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
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
	
	protected void parseNotification(Notification notification) {
		Map<Integer, String> textMap = new HashMap<Integer, String>();
		RemoteViews view = notification.contentView;
		try {
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
					
					if ("setText".equals(methodName.get(action).toString())) {
						textMap.put(viewId.getInt(action), value.get(action).toString());
					}
				}
			}
			
			if (notification.tickerText != null) {
				ticker = notification.tickerText.toString();
			}			
			title = textMap.get(android.R.id.title);				
			text = textMap.get(textId);
			number = notification.number;
			iconId = notification.icon;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public JSONObject toJSON(int key, boolean hasIcon) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		JSONObject json = new JSONObject();
		try {
			json.put("action", action);
			json.put("key", key);
			
			JSONObject notify = new JSONObject();			
			notify.put("package", pkg);
			notify.put("id", id);
			notify.put("tag", tag);
			notify.put("number", number);
			notify.put("name", name);
			notify.put("ticker", ticker);
			notify.put("title", title);
			notify.put("text", text);
			notify.put("iconId", iconId);
			if (hasIcon && icon != null) {
				icon.compress(CompressFormat.PNG, 100, buffer);
				notify.put("icon", Base64.encodeToString(buffer.toByteArray(), Base64.DEFAULT));
			}
			
			json.put("notification", notify);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return json;
	}
}*/