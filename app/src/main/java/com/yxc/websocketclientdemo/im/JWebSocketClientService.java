package com.yxc.websocketclientdemo.im;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.yxc.websocketclientdemo.MainActivity;
import com.yxc.websocketclientdemo.R;
import com.yxc.websocketclientdemo.modle.ChatMessage;
import com.yxc.websocketclientdemo.util.Util;

import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

public class JWebSocketClientService extends Service {
    public JWebSocketClient client;
    private JWebSocketClientBinder mBinder = new JWebSocketClientBinder();
    private final static int GRAY_SERVICE_ID = 1001;
    String CHANNEL_ONE_ID = "com.yxc.websocketclient";
    String CHANNEL_TWO_ID = "com.yxc.websocketclient.receive";

    //灰色保活
    public static class GrayInnerService extends Service {

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(GRAY_SERVICE_ID, new Notification());
            stopForeground(true);
            stopSelf();
            return super.onStartCommand(intent, flags, startId);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    PowerManager.WakeLock wakeLock;//锁屏唤醒

    //获取电源锁，保持该服务在屏幕熄灭时仍然获取CPU时，保持运行
    @SuppressLint("InvalidWakeLockTag")
    private void acquireWakeLock() {
        if (null == wakeLock) {
            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "PostLocationService");
            if (null != wakeLock) {
                wakeLock.acquire();
            }
        }
    }

    private void setAlarm() {

        //创建Alarm并启动
        Intent intent = new Intent("HEART_CLOCK");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
// 每五秒唤醒一次
        long second = 15 * 1000;
        second = System.currentTimeMillis() + 15 * 1000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, second,
                    pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, second,
                    pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, second,
                    pendingIntent);
        }
    }

    public class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("HEART_CLOCK")) {
                Log.d("timer", "--->>>   onReceive  LOCATION_CLOCK");
                Intent mIntent = new Intent(context, JWebSocketClientService.class);
                mIntent.putExtra("command", "timer");
                context.startService(mIntent);
            }
        }
    }


    //用于Activity和service通讯
    public class JWebSocketClientBinder extends Binder {
        public JWebSocketClientService getService() {
            return JWebSocketClientService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setForegroundNotify();
        setAlarm();

    }

    /**
     * 设置前台通知
     */
    private void setForegroundNotify() {
        //设置service为前台服务，提高优先级
        if (Build.VERSION.SDK_INT < 18) {
            //Android4.3以下 ，隐藏Notification上的图标
            startForeground(GRAY_SERVICE_ID, new Notification());
        } else if (Build.VERSION.SDK_INT > 18 && Build.VERSION.SDK_INT < 25) {
            //Android4.3 - Android7.0，隐藏Notification上的图标
            Intent innerIntent = new Intent(this, GrayInnerService.class);
            startService(innerIntent);
            startForeground(GRAY_SERVICE_ID, new Notification());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            //Android7.0以上app启动后通知栏会出现一条"正在运行"的通知

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = null;
            NotificationManager mManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Uri mUri = Settings.System.DEFAULT_NOTIFICATION_URI;
            NotificationChannel mChannel = null;
            mChannel = new NotificationChannel(CHANNEL_ONE_ID, "消息服务", NotificationManager.IMPORTANCE_LOW);
            mChannel.setDescription("消息连接服务");
            mChannel.setSound(mUri, Notification.AUDIO_ATTRIBUTES_DEFAULT);
            mManager.createNotificationChannel(mChannel);
            notification = new Notification.Builder(this, CHANNEL_ONE_ID)
                    .setChannelId(CHANNEL_ONE_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("服务器消息")
                    .setContentText("消息连接服务")
                    .setContentIntent(pi)
                    .build();
            startForeground(GRAY_SERVICE_ID, notification);
        } else {
            startForeground(GRAY_SERVICE_ID, new Notification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String command = intent.getStringExtra("command");
            if (!TextUtils.isEmpty(command)) {
                if (TextUtils.equals(command, "timer")) {
                    setAlarm();
                }
            } else {
                initSocketClient();
                //开启心跳检测
                mHandler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);
                acquireWakeLock();
            }
        } else {
            initSocketClient();
            //开启心跳检测
            mHandler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);
            acquireWakeLock();
        }


        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        closeConnect();
        super.onDestroy();
    }

    public JWebSocketClientService() {
    }


    /**
     * 初始化websocket连接
     */
    private void initSocketClient() {
        SharedPreferences sharedPreferences = getSharedPreferences("data", Context.MODE_PRIVATE);
        String target = sharedPreferences.getString("target", "");
        URI uri;


        if (TextUtils.isEmpty(target)) {
            uri = URI.create(Util.ws);
        } else {
            uri = URI.create(target);
        }

        if (client != null && !client.getURI().equals(uri) && !client.isClosed()) {
            //uri
            //需要先关闭连接 再重新连接
            closeConnect();
        }
        if (client == null || client.isClosed()) {
            client = new JWebSocketClient(uri) {
                @Override
                public void onMessage(String message) {
                    Log.e("JWebSocketClientService", "收到的消息：" + message);
                    Intent intent = new Intent();
                    intent.setAction("com.xch.servicecallback.content");
                    intent.putExtra("message", message);
                    sendBroadcast(intent);
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.setContent(message);
                    chatMessage.setIsMeSend(0);
                    chatMessage.setIsRead(1);
                    chatMessage.setTime(System.currentTimeMillis() + "");
                    chatMessage.save();
                    checkLockAndShowNotification(message);
                }

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    super.onOpen(handshakedata);
                    Log.e("JWebSocketClientService", "websocket连接成功");
                }
            };
            connect();
        }


    }


    /**
     * 连接websocket
     */
    private void connect() {
        new Thread() {
            @Override
            public void run() {
                try {
                    //connectBlocking多出一个等待操作，会先连接再发送，否则未连接发送会报错
                    client.connectBlocking();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();

    }

    /**
     * 发送消息
     *
     * @param msg
     */
    public void sendMsg(String msg) {
        if (null != client) {
            JSONObject o = new JSONObject();
            try {
                o.put("msg", msg);
                msg = o.toString();
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Log.e("JWebSocketClientService", "发送的消息：" + msg);
            client.send(msg);
        }
    }

    /**
     * 断开连接
     */
    private void closeConnect() {
        try {
            if (null != client) {
                client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            client = null;
        }
    }


//    -----------------------------------消息通知--------------------------------------------------------

    /**
     * 检查锁屏状态，如果锁屏先点亮屏幕
     *
     * @param content
     */
    private void checkLockAndShowNotification(String content) {
        //管理锁屏的一个服务
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode()) {//锁屏
            //获取电源管理器对象
            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            if (!pm.isScreenOn()) {
                @SuppressLint("InvalidWakeLockTag") PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
                wl.acquire();  //点亮屏幕
                wl.release();  //任务结束后释放
            }
            sendNotification(content);
        } else {
            sendNotification(content);
        }
    }

    /**
     * 发送通知
     *
     * @param content
     */
    private void sendNotification(String content) {

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = null;
        NotificationManager mManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            Uri mUri = Settings.System.DEFAULT_NOTIFICATION_URI;

            NotificationChannel mChannel = new NotificationChannel(CHANNEL_TWO_ID, "接受消息", NotificationManager.IMPORTANCE_LOW);

            mChannel.setDescription("接受到消息");

            mChannel.setSound(mUri, Notification.AUDIO_ATTRIBUTES_DEFAULT);

            mManager.createNotificationChannel(mChannel);

            notification = new Notification.Builder(this, CHANNEL_TWO_ID)
                    .setChannelId(CHANNEL_TWO_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("服务器")
                    .setContentText(content)
                    .setContentIntent(pi)
                    .build();
        } else {
            // 提升应用权限
            notification = new Notification.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("服务器")
                    .setContentText(content)
                    .setContentIntent(pi)
                    .build();
        }
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;
        startForeground(20000, notification);


    }


    //    -------------------------------------websocket心跳检测------------------------------------------------
    private static final long HEART_BEAT_RATE = 10 * 1000;//每隔10秒进行一次对长连接的心跳检测
    private Handler mHandler = new Handler();
    private Runnable heartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            Log.e("JWebSocketClientService", "心跳包检测websocket连接状态");
            if (client != null) {
                if (client.isClosed()) {
                    reconnectWs();
                }
            } else {
                //如果client已为空，重新初始化连接
                client = null;
                initSocketClient();
            }
            //每隔一定的时间，对长连接进行一次心跳检测
            mHandler.postDelayed(this, HEART_BEAT_RATE);
        }
    };

    /**
     * 开启重连
     */
    private void reconnectWs() {
        mHandler.removeCallbacks(heartBeatRunnable);
        new Thread() {
            @Override
            public void run() {
                try {
                    Log.e("JWebSocketClientService", "开启重连");
                    client.reconnectBlocking();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}
