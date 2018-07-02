package com.example.admin.hookwxvoicecall;

import android.media.AudioRecord;
import android.util.Log;
import java.util.HashMap;
import java.util.Random;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;



public class Module implements IXposedHookLoadPackage {

    private static final String TAG = "voiceCallModule";

    // 每个AudioRecord对应的state状态，用于维护当前录音状态
    private HashMap<AudioRecord,Integer> mRecordingFlagMap = new HashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if(loadPackageParam.packageName.equals("com.tencent.mm")){
            Log.i(TAG, "handleLoadPackage: hook了微信");


            XposedHelpers.findAndHookConstructor("android.media.AudioRecord", loadPackageParam.classLoader,
                    int.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.i(TAG, "AudioRecord # 构造方法beforeHookedMethod: ");

                            int audioSource = (int)param.args[0];
                            int sampleRateInHz = (int)param.args[1];
                            int channelConfig = (int)param.args[2];
                            int audioFormat = (int)param.args[3];
                            int bufferSizeInBytes = (int)param.args[4];

                            Log.i(TAG, "beforeHookedMethod: 构造方法 audioSource -- "+audioSource);  // 1
                            Log.i(TAG, "beforeHookedMethod: 构造方法 sampleRateInHz -- "+sampleRateInHz);// 16000
                            Log.i(TAG, "beforeHookedMethod: 构造方法 channelConfig -- "+channelConfig); // 2
                            Log.i(TAG, "beforeHookedMethod: 构造方法 audioFormat -- "+audioFormat); // 2
                            Log.i(TAG, "beforeHookedMethod: 构造方法 bufferSizeInBytes -- "+bufferSizeInBytes);// 12800  模拟器15360

                            //  修改
                            param.args[0] = 1;
                            param.args[1] = 16000;
                            param.args[2] = 2;
                            param.args[3] = 2;
                            param.args[4] = 12800;

                        }

                    });

            XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                    loadPackageParam.classLoader, "startRecording", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.i(TAG, "AudioRecord#startRecording beforeHookedMethod: ");

                            AudioRecord record = (AudioRecord) param.thisObject;
                            int flag = -1;

                            // 将录音状态置为RECORDSTATE_RECORDING状态
                            if (mRecordingFlagMap.get(record) == null || mRecordingFlagMap.get(record) != AudioRecord.RECORDSTATE_RECORDING) {
                                flag = AudioRecord.RECORDSTATE_RECORDING;
                                mRecordingFlagMap.put(record, flag);
                            }

                            // 打断微信的录音过程
                            Object o = new Object();
                            param.setResult(o);

                        }

                    });

            // hook  getRecordingState 方法，当发送指定语音时需要hook这个函数
            XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                    loadPackageParam.classLoader, "getRecordingState", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.i(TAG, "AudioRecord#getRecordingState beforeHookedMethod: ");

                            // 获取我们在startRecording和Stop中维护的state的值
                            AudioRecord record = (AudioRecord) param.thisObject;
                            int res = mRecordingFlagMap.get(record) == null ? AudioRecord.RECORDSTATE_STOPPED : mRecordingFlagMap.get(record);
                            // 将返回值给微信
                            param.setResult(res);
                            // 清理mRecordFlagMap
                            mRecordingFlagMap.remove(record);

                        }
                    });


            // hook  read 方法，当发送指定语音时需要hook这个函数需要在before操作，当录入自己的语音文件时需要在after操作
            XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                    loadPackageParam.classLoader, "read", byte[].class, int.class,
                    int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.i(TAG, "AudioRecord#read beforeHookedMethod: ");

                            int seed = new Random().nextInt(10);
                            Log.i(TAG, "beforeHookedMethod: seed -- "+seed);
                            // 真机得4以上才有，模拟器1就可以了
                            if(seed > 1){
                                param.setResult(0);
                            }else {

                                byte[] buffer = (byte[]) param.args[0];
                                int off = (int) param.args[1];
                                int size = (int) param.args[2];

                                int min = Math.min(buffer.length - off, size);
                                byte[] bytes = new byte[min];
                                // 赋予随机值
                                new Random().nextBytes(bytes);

                                for (int i = 0;i < bytes.length; i++) {
                                    buffer[off + i] = bytes[i];
                                }
                                param.setResult(bytes.length);

                            }
                        }

                    });


            // hook  stop 方法，当发送指定语音时需要hook这个函数需要在before操作，当录入自己的语音文件时需要在after操作
            XposedHelpers.findAndHookMethod("android.media.AudioRecord",
                    loadPackageParam.classLoader, "stop", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.i(TAG, "AudioRecord#stop beforeHookedMethod: ");

                            AudioRecord record = (AudioRecord) param.thisObject;

                            // 将录音状态设置为stopped
                            int flag = -1;
                            if (mRecordingFlagMap.get(record) == null || mRecordingFlagMap.get(record) != AudioRecord.RECORDSTATE_STOPPED) {
                                flag = AudioRecord.RECORDSTATE_STOPPED;
                                mRecordingFlagMap.put(record, flag);
                            }
                            //  打断微信，完成发送指定的语音文件
                            Object o = new Object();
                            param.setResult(o);
                        }

                    });


            // hook  release 方法，当发送指定语音时需要hook这个函数，打断微信
            XposedHelpers.findAndHookMethod("android.media.AudioRecord", loadPackageParam.classLoader,
                    "release", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Log.i(TAG, "AudioRecord#release beforeHookedMethod: ");

                            Object o = new Object();
                            param.setResult(o);
                        }


                    });

        }
    }
}
