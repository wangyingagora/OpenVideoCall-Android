package io.agora.openvcall;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.xiaomi.mipush.sdk.MiPushClient;
import com.xiaomi.xmpush.server.Constants;
import com.xiaomi.xmpush.server.Message;
import com.xiaomi.xmpush.server.Result;
import com.xiaomi.xmpush.server.Sender;

import org.json.simple.parser.ParseException;

import java.io.IOException;

import io.agora.openvcall.model.CurrentUserSettings;
import io.agora.openvcall.model.WorkerThread;

public class AGApplication extends Application {
    private final static String TAG = AGApplication.class.getSimpleName();

    private WorkerThread mWorkerThread;

    // user your appid the key.
    private static final String APP_ID = "";
    // user your appid the key.
    private static final String APP_KEY = "";

    private static final String APP_SECRET_KEY = "";

    private static final int COMMAND_START_CALL = 1;

    private static MessageHandler sHandler = null;
    private static MediaPlayer mPlayer = null;

    private static AGApplication mThis;

    private static String CALLER = "";
    private static String CALLEE = "";

    private static ServerHandler mServerHandler;
    private HandlerThread mServerThread;

    private static final String AGORA_PREFERENCES = "agora_perfs" ;
    private static final String AGORA_PREFERENCES_CALLER = "caller" ;

    private static class ServerHandler extends Handler {
        public ServerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case COMMAND_START_CALL:
                    AGApplication.startCall();
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mThis = this;

        mServerThread = new HandlerThread("AgoraServerThread");
        mServerThread.start();
        mServerHandler = new ServerHandler(mServerThread.getLooper());

        if (sHandler == null) {
            sHandler = new MessageHandler(getApplicationContext());
        }
        MiPushClient.registerPush(this, APP_ID, APP_KEY);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (mServerThread != null) {
            mServerThread.quit();
        }
        mServerHandler = null;
    }

    public synchronized void initWorkerThread() {
        if (mWorkerThread == null) {
            mWorkerThread = new WorkerThread(getApplicationContext());
            mWorkerThread.start();

            mWorkerThread.waitForReady();
        }
    }

    public synchronized WorkerThread getWorkerThread() {
        return mWorkerThread;
    }

    public synchronized void deInitWorkerThread() {
        mWorkerThread.exit();
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mWorkerThread = null;
    }

    public static final CurrentUserSettings mVideoSettings = new CurrentUserSettings();

    public static MessageHandler getHandler() {
        return sHandler;
    }

    public static void setAlias(String alias) {
        if (TextUtils.isEmpty(alias)) {
            return;
        }
        if (alias.equals("wyn")) {
            CALLER = "wyn";
            CALLEE = "baba";
        } else if (alias.equals("baba")) {
            CALLER = "baba";
            CALLEE = "wyn";
        }
        MiPushClient.setAlias(mThis, alias, null);
    }

    public static class MessageHandler extends Handler {

        private Context context;

        public MessageHandler(Context context) {
            this.context = context;
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            int command = msg.arg1;
            if (command == AgoraMessageReceiver.MI_REGISTER_SUCCESS_COMMAND) {
                String s = (String) msg.obj;
                Toast.makeText(context, s, Toast.LENGTH_LONG).show();
            } else if (command == AgoraMessageReceiver.MI_SET_ALIAS_SUCCESS_COMMAND) {
                String s = (String) msg.obj;
                Toast.makeText(context, s, Toast.LENGTH_LONG).show();
                android.os.Message message = new android.os.Message();
                message.what = COMMAND_START_CALL;
                mServerHandler.sendMessage(message);

                mThis.saveCaller(CALLER);
                /*
                if (!TextUtils.isEmpty(CALLEE)) {
                    android.os.Message message = new android.os.Message();
                    message.what = COMMAND_START_CALL;
                    mServerHandler.sendMessage(message);
                }
                */
            } else if (command == AgoraMessageReceiver.MI_PASS_THROUGH_MESSAGE_COMMAND) {
                String s = (String) msg.obj;
                Toast.makeText(context, s, Toast.LENGTH_LONG).show();
                String[] parts = s.split(":");
                if (parts.length != 4) return;
                String caller = parts[1].trim();
                String callee = parts[3].trim();
                if (caller.equals(CALLER)) return;
                AGApplication.playMusic();
            }
            /*
            String s = (String) msg.obj;
            if (!TextUtils.isEmpty(s)) {
                Toast.makeText(context, s, Toast.LENGTH_LONG).show();
            }
            */
        }
    }

    public static void playMusic() {
        if (mPlayer == null) {
            mPlayer = MediaPlayer.create(mThis, R.raw.five_hundred_miles);
        }
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
            mPlayer.seekTo(0);
            // mPlayer.reset();
        }

        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setLooping(false);
        mPlayer.start();
    }

    public static void stopMusic() {
        if (mPlayer != null) {
            mPlayer.pause();
            mPlayer.seekTo(0);
            // mPlayer.reset();
        }
    }

    // Send MI message
    public static void startCall() {
        Constants.useOfficial();
        Sender sender = new Sender(APP_SECRET_KEY);
        String PACKAGENAME = mThis.getPackageName();
        String messagePayload = "caller:" + CALLER + ":callee:" + CALLEE;
        Message message = new Message.Builder()
                .payload(messagePayload)
                .restrictedPackageName(PACKAGENAME)
                .passThrough(1)  // 消息使用透传方式
                .notifyType(-1)   // 使用默认提示音提示
                .build();
        try {
            Result result = sender.sendToAlias(message, CALLEE, 3);
            Log.v("Server response: ", "MessageId: " + result.getMessageId()
                    + " ErrorCode: " + result.getErrorCode().toString()
                    + " Reason: " + result.getReason());
        } catch (IOException ex) {
            Log.e(TAG, ex.toString());
        } catch (ParseException ex) {
            Log.e(TAG, ex.toString());
        }

        //return message;
    }

    private void saveCaller(String caller) {
        if (TextUtils.isEmpty(caller)) return;

        SharedPreferences.Editor editor = getSharedPreferences(AGORA_PREFERENCES, MODE_PRIVATE).edit();
        editor.putString(AGORA_PREFERENCES_CALLER, caller);
        editor.commit();
    }

    public static String getCaller() {
        SharedPreferences perfs = mThis.getSharedPreferences(AGORA_PREFERENCES, MODE_PRIVATE);
        return perfs.getString(AGORA_PREFERENCES_CALLER, "");
    }
 }
