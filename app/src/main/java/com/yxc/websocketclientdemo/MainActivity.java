package com.yxc.websocketclientdemo;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.tbruyelle.rxpermissions2.RxPermissions;
import com.yxc.websocketclientdemo.adapter.Adapter_ChatMessage;
import com.yxc.websocketclientdemo.adapter.HorizontalRecyclerviewAdapter;
import com.yxc.websocketclientdemo.adapter.NoHorizontalScrollerVPAdapter;
import com.yxc.websocketclientdemo.customview.audiorecord.AudioRecorderButton;
import com.yxc.websocketclientdemo.customview.emj.EmotionKeyboard;
import com.yxc.websocketclientdemo.fragment.EmojiFragment;
import com.yxc.websocketclientdemo.fragment.TestFragment;
import com.yxc.websocketclientdemo.im.JWebSocketClient;
import com.yxc.websocketclientdemo.im.JWebSocketClientService;
import com.yxc.websocketclientdemo.modle.ChatMessage;
import com.yxc.websocketclientdemo.modle.ImageModel;
import com.yxc.websocketclientdemo.util.Lemoji;
import com.yxc.websocketclientdemo.util.LeoOnItemClickManagerUtils;
import com.yxc.websocketclientdemo.util.Util;

import org.litepal.LitePal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.rockerhieu.emojicon.EmojiconEditText;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Context mContext;
    private JWebSocketClient client;
    private JWebSocketClientService.JWebSocketClientBinder binder;
    private JWebSocketClientService jWebSClientService;
    private EditText etUrl;
    private EmojiconEditText et_content;
    private ListView listView;
    private TextView btn_send;
    private ViewPager viewPager;
    private RecyclerView recyclerviewHorizontal;
    private ImageView img_voice; //切换语音按钮
    private ImageView emotion_button;//表情按钮
    private AudioRecorderButton audioRecordButton;//语音按钮
    //申请权限
    private RxPermissions rxPermissions;
    //表情面板
    private EmotionKeyboard mEmotionKeyboard;
    private Button btnConnect;


    /**
     * 底部表情的
     * */
    private HorizontalRecyclerviewAdapter horizontalRecyclerviewAdapter;
    private ArrayList<Fragment> fragments = new ArrayList<>();
    ArrayList<ImageModel> sourceList = new ArrayList<>();
    private int oldPosition = 0;

    //消息列表
    private List<ChatMessage> chatMessageList = new ArrayList<>();
    private Adapter_ChatMessage adapter_chatMessage;
    private ChatMessageReceiver chatMessageReceiver;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.e("MainActivity", "服务与活动成功绑定");
            binder = (JWebSocketClientService.JWebSocketClientBinder) iBinder;
            jWebSClientService = binder.getService();
            client = jWebSClientService.client;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e("MainActivity", "服务与活动成功断开");
        }
    };

    private class ChatMessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setContent(message);
            chatMessage.setIsMeSend(0);
            chatMessage.setIsRead(1);
            chatMessage.setTime(System.currentTimeMillis() + "");
            chatMessageList.add(chatMessage);
//            chatMessage.save();
            notifyChangeData();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        mContext = MainActivity.this;
        rxPermissions = new RxPermissions(this);
        //启动服务
        startJWebSClientService();
        //绑定服务
        bindService();
        //注册广播
        doRegisterReceiver();
        //检测通知是否开启
        checkNotification(mContext);
        findViewById();
        initView();
        initData();
        initEmj();
        initVoiceRecord();
        initEmjData();
    }

    private void initData() {
        List<ChatMessage> temp = LitePal.findAll(ChatMessage.class);
        chatMessageList.addAll(temp);

        int size = chatMessageList.size();
        if (size > 0) {
            adapter_chatMessage.notifyDataSetChanged();
            listView.setSelection(size);
        }
    }

    /**
     * 绑定服务
     */
    private void bindService() {
        Intent bindIntent = new Intent(mContext, JWebSocketClientService.class);
        bindService(bindIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    /**
     * 启动服务（websocket客户端服务）
     */
    private void startJWebSClientService() {
        Intent intent = new Intent(mContext, JWebSocketClientService.class);
        startService(intent);
    }

    /**
     * 动态注册广播
     */
    private void doRegisterReceiver() {
        chatMessageReceiver = new ChatMessageReceiver();
        IntentFilter filter = new IntentFilter("com.xch.servicecallback.content");
        registerReceiver(chatMessageReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
        unregisterReceiver(chatMessageReceiver);

    }

    private void findViewById() {
        img_voice = findViewById(R.id.img_voice);
        etUrl = findViewById(R.id.etUrl);
        btnConnect = findViewById(R.id.btnConnect);
        emotion_button = findViewById(R.id.emotion_button);
        audioRecordButton = findViewById(R.id.audioRecordButton);
        viewPager = findViewById(R.id.viewPager);
        recyclerviewHorizontal = findViewById(R.id.recyclerview_horizontal);

        listView = findViewById(R.id.chatmsg_listView);
        btn_send = findViewById(R.id.text_send);
        et_content = findViewById(R.id.bar_edit_text);
        btnConnect.setOnClickListener(this);

        btn_send.setOnClickListener(this);

        SharedPreferences sharedPreferences = getSharedPreferences("data", Context.MODE_PRIVATE);
        String target = sharedPreferences.getString("target", "");
        etUrl.setText(target);

        String url = etUrl.getText().toString();
        if (TextUtils.isEmpty(url)) {
            etUrl.setText(Util.ws);
        }
    }

    private void initView() {
        adapter_chatMessage = new Adapter_ChatMessage(mContext, chatMessageList);
        listView.setAdapter(adapter_chatMessage);
        //监听输入框的变化
        et_content.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (et_content.getText().toString().length() > 0) {
                    btn_send.setVisibility(View.VISIBLE);
                } else {
                    btn_send.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnConnect:
                String url = etUrl.getText().toString();
                //步骤1：创建一个SharedPreferences对象
                SharedPreferences sharedPreferences = getSharedPreferences("data", Context.MODE_PRIVATE);
                //步骤2： 实例化SharedPreferences.Editor对象
                SharedPreferences.Editor editor = sharedPreferences.edit();
                //步骤3：将获取过来的值放入文件
                editor.putString("target", url);
                //步骤4：提交
                editor.commit();
                startJWebSClientService();
                break;

            case R.id.text_send:
                String content = et_content.getText().toString();
                if (content.length() <= 0) {
                    Util.showToast(mContext, "消息不能为空哟");
                    return;
                }

                if (client != null && client.isOpen()) {
                    jWebSClientService.sendMsg(content);

                    //暂时将发送的消息加入消息列表，实际以发送成功为准（也就是服务器返回你发的消息时）
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.setContent(content);
                    chatMessage.setIsMeSend(1);
                    chatMessage.setIsRead(1);
                    chatMessage.setTime(System.currentTimeMillis() + "");
                    chatMessageList.add(chatMessage);
                    notifyChangeData();
                    et_content.setText("");
                    chatMessage.save();
                } else {
                    Util.showToast(mContext, "连接已断开，请稍等或重启App哟");
                }
                break;
            default:
                break;
        }
    }

    private void notifyChangeData() {
        adapter_chatMessage.notifyDataSetChanged();
        listView.setSelection(chatMessageList.size());
    }


    /**
     * 检测是否开启通知
     *
     * @param context
     */
    private void checkNotification(final Context context) {
        if (!isNotificationEnabled(context)) {
            new AlertDialog.Builder(context).setTitle("温馨提示")
                    .setMessage("你还未开启系统通知，将影响消息的接收，要去开启吗？")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            setNotification(context);
                        }
                    }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            }).show();
        }
    }

    /**
     * 如果没有开启通知，跳转至设置界面
     *
     * @param context
     */
    private void setNotification(Context context) {
        Intent localIntent = new Intent();
        //直接跳转到应用通知设置的代码：
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            localIntent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            localIntent.putExtra("app_package", context.getPackageName());
            localIntent.putExtra("app_uid", context.getApplicationInfo().uid);
        } else if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            localIntent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            localIntent.addCategory(Intent.CATEGORY_DEFAULT);
            localIntent.setData(Uri.parse("package:" + context.getPackageName()));
        } else {
            //4.4以下没有从app跳转到应用通知设置页面的Action，可考虑跳转到应用详情页面,
            localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 9) {
                localIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                localIntent.setData(Uri.fromParts("package", context.getPackageName(), null));
            } else if (Build.VERSION.SDK_INT <= 8) {
                localIntent.setAction(Intent.ACTION_VIEW);
                localIntent.setClassName("com.android.settings", "com.android.setting.InstalledAppDetails");
                localIntent.putExtra("com.android.settings.ApplicationPkgName", context.getPackageName());
            }
        }
        context.startActivity(localIntent);
    }

    /**
     * 获取通知权限,监测是否开启了系统通知
     *
     * @param context
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean isNotificationEnabled(Context context) {

        String CHECK_OP_NO_THROW = "checkOpNoThrow";
        String OP_POST_NOTIFICATION = "OP_POST_NOTIFICATION";

        AppOpsManager mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        ApplicationInfo appInfo = context.getApplicationInfo();
        String pkg = context.getApplicationContext().getPackageName();
        int uid = appInfo.uid;

        Class appOpsClass = null;
        try {
            appOpsClass = Class.forName(AppOpsManager.class.getName());
            Method checkOpNoThrowMethod = appOpsClass.getMethod(CHECK_OP_NO_THROW, Integer.TYPE, Integer.TYPE,
                    String.class);
            Field opPostNotificationValue = appOpsClass.getDeclaredField(OP_POST_NOTIFICATION);

            int value = (Integer) opPostNotificationValue.get(Integer.class);
            return ((Integer) checkOpNoThrowMethod.invoke(mAppOps, value, uid, pkg) == AppOpsManager.MODE_ALLOWED);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void initVoiceRecord() {
        audioRecordButton.setAudioFinishRecorderListener(new AudioRecorderButton.AudioFinishRecorderListener() {
            @Override
            public void onFinish(int seconds, String FilePath) {

                Log.d("audio path",FilePath);
//                ChatBean chatBean9 = new ChatBean();
//                chatBean9.setType(9);
//                chatBean9.setSeconds(seconds);
//                chatBean9.setAudioPath(FilePath);
//                chatList.add(chatBean9);
//                adapter.notifyDataSetChanged();
//                binding.recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }
    private void initEmjData() {
        /*
         * 注意这里如果只用到系统表情可用GlobalOnItemClickManagerUtils
         *
         * 既支持系统表情  又支持 自定义表情 用LeoOnItemClickManagerUtils 具体我会讲解。
         * */
        LeoOnItemClickManagerUtils.getInstance(this).attachToEditText(et_content);


        ImageModel model1 = new ImageModel();
        model1.icon = getResources().getDrawable(R.mipmap.emj_xiao);
        model1.flag = "经典笑脸";
        model1.isSelected = true;
        sourceList.add(model1);

        for (int i = 0; i < 4; i++) {
            if (i == 0) {
                ImageModel model2 = new ImageModel();
                model2.icon = getResources().getDrawable(R.mipmap.gole);
                model2.flag = "其他";
                model2.isSelected = false;
                sourceList.add(model2);
            } else if (i == 1) {
                ImageModel model2 = new ImageModel();
                model2.icon = getResources().getDrawable(R.drawable.dding1);
                model2.flag = "逗比";
                model2.isSelected = false;
                sourceList.add(model2);
            } else {
                ImageModel model2 = new ImageModel();
                model2.icon = getResources().getDrawable(R.mipmap.emj_add);
                model2.flag = "其他";
                model2.isSelected = false;
                sourceList.add(model2);
            }

        }

        //底部tab
        horizontalRecyclerviewAdapter = new HorizontalRecyclerviewAdapter(this, sourceList);
        recyclerviewHorizontal.setHasFixedSize(true);//使RecyclerView保持固定的大小,这样会提高RecyclerView的性能
        recyclerviewHorizontal.setAdapter(horizontalRecyclerviewAdapter);
        recyclerviewHorizontal.setLayoutManager(new GridLayoutManager(this, 1, GridLayoutManager.HORIZONTAL, false));
        //初始化recyclerview_horizontal监听器
        horizontalRecyclerviewAdapter.setOnClickItemListener(new HorizontalRecyclerviewAdapter.OnClickItemListener() {
            @Override
            public void onItemClick(View view, int position, List<ImageModel> datas) {
                //修改背景颜色的标记
                datas.get(oldPosition).isSelected = false;
                //记录当前被选中tab下标
                datas.get(position).isSelected = true;
                //通知更新，这里我们选择性更新就行了
                horizontalRecyclerviewAdapter.notifyItemChanged(oldPosition);
                horizontalRecyclerviewAdapter.notifyItemChanged(position);

                //viewpager界面切换
                viewPager.setCurrentItem(position, false);
                oldPosition = position;
            }

            @Override
            public void onItemLongClick(View view, int position, List<ImageModel> datas) {
            }
        });

        fragments.add(EmojiFragment.newInstance(Lemoji.DATA));
        fragments.add(EmojiFragment.newInstance(Lemoji.SUNDATA));
        fragments.add(EmojiFragment.newInstance(Lemoji.MYFACE));
        fragments.add(TestFragment.newInstance(4));
        fragments.add(TestFragment.newInstance(5));

        NoHorizontalScrollerVPAdapter adapter = new NoHorizontalScrollerVPAdapter(getSupportFragmentManager(), fragments);
        viewPager.setAdapter(adapter);


        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {

            }

            @Override
            public void onPageSelected(int position) {

                //修改背景颜色的标记
                sourceList.get(oldPosition).isSelected = false;
                //记录当前被选中tab下标
                sourceList.get(position).isSelected = true;
                //通知更新，这里我们选择性更新就行了
                horizontalRecyclerviewAdapter.notifyItemChanged(oldPosition);
                horizontalRecyclerviewAdapter.notifyItemChanged(position);

                //viewpager界面切换
                viewPager.setCurrentItem(position, false);
                oldPosition = position;

            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });


    }

    private void initEmj() {
        mEmotionKeyboard = EmotionKeyboard.with(this)
                .setEmotionView(findViewById(R.id.ll_emotion_layout))//绑定表情面板
                .bindToRxPerimission(rxPermissions)
                .bindToContent(listView)//绑定内容view
                .bindToEditText(et_content)//判断绑定那种EditView
                .bindToEmotionButton(emotion_button)//绑定表情按钮
                .bindToVoiceButton(img_voice)
                .bindToVoiceStart(audioRecordButton)
                .bindToSend(btn_send)
                .build();

        /*
         * 注意这里如果只用到系统表情可用GlobalOnItemClickManagerUtils
         *
         * 既支持系统表情  又支持 自定义表情 用LeoOnItemClickManagerUtils 具体我会讲解。
         * */
//        GlobalOnItemClickManagerUtils.getInstance(MainActivity.this).attachToEditText(edit_content);

//        text_send.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                ChatBean chatBean = new ChatBean();
//                chatBean.setType(7);
//                chatBean.setMessage(edit_content.getText().toString());
//                edit_content.setText("");
//                chatList.add(chatBean);
//                adapter.notifyDataSetChanged();
//                binding.recyclerView.scrollToPosition(adapter.getItemCount() - 1);
//            }
//        });
    }
}
