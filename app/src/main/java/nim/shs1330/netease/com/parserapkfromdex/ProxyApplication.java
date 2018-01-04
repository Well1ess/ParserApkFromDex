package nim.shs1330.netease.com.parserapkfromdex;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexClassLoader;

/**
 * Created by shs1330 on 2017/12/28.
 */

public class ProxyApplication extends Application {
    private static final String TAG = "ProxyApplication";

    private String apkFileName;
    private String odexPath;
    private String libPath;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        File odex = this.getDir("src_odex", MODE_PRIVATE);
        Log.d(TAG, "attachBaseContext: " + odex.getPath());

        File libs = this.getDir("src_lib", MODE_PRIVATE);
        Log.d(TAG, "attachBaseContext: " + libs.getAbsolutePath());


        odexPath = odex.getAbsolutePath();
        libPath = libs.getAbsolutePath();
        apkFileName = odexPath + "/src.apk";

        File dexFile = new File(apkFileName);
        try {


            dexFile.createNewFile();
            byte[] dexdata = this.readDexFileFromApk();
            Log.d(TAG, "原声 46084B" + " attachBaseContext: " + dexdata.length);

            splitSrcApk(dexdata);


            Class activityThreadC = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadM = activityThreadC.getDeclaredMethod("currentActivityThread");
            currentActivityThreadM.setAccessible(true);
            Object activityThreadO = currentActivityThreadM.invoke(null);

            String packageName = this.getPackageName(); //当前package Name

            Field mPackagesF = activityThreadC.getDeclaredField("mPackages");
            mPackagesF.setAccessible(true);
            ArrayMap mPackagesO = (ArrayMap) mPackagesF.get(activityThreadO);
            //从AT中获取壳apk的LoadedApk，因为里面存放了ClassLoader
            WeakReference wr = (WeakReference) mPackagesO.get(packageName);
            //获取原来的夫加载器
            Class LoadedApkC = Class.forName("android.app.LoadedApk");
            Field mClassLoaderF = LoadedApkC.getDeclaredField("mClassLoader");
            mClassLoaderF.setAccessible(true);
            ClassLoader parentLoader = (ClassLoader) mClassLoaderF.get(wr.get());
            //生命我们源apk的loader
            DexClassLoader loader = new DexClassLoader(apkFileName, odexPath, libPath, parentLoader);

            mClassLoaderF.set(wr.get(), loader);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onCreate() {
        super.onCreate();
        String appClassName = null;
        try {
            ApplicationInfo appInfo = this.getPackageManager()
                    .getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = appInfo.metaData;
            if (bundle != null && bundle.containsKey("APP_NAME")) {
                appClassName = bundle.getString("APP_NAME");
            }


            Class activityThreadC = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadM = activityThreadC.getDeclaredMethod("currentActivityThread");
            currentActivityThreadM.setAccessible(true);
            //当前ActivityThread实例
            Object activityThreadO = currentActivityThreadM.invoke(null);

            Field mBoundApplicationF = activityThreadC.getDeclaredField("mBoundApplication");
            mBoundApplicationF.setAccessible(true);
            //AppBindData对象
            Object boundApplicationO = mBoundApplicationF.get(activityThreadO);

            Class appBindData = Class.forName("android.app.ActivityThread$AppBindData");
            Field loadedApkF = appBindData.getDeclaredField("info");
            loadedApkF.setAccessible(true);
            //LoadedApk对象
            Object loadedApkO = loadedApkF.get(boundApplicationO);

            Class loadedApkC = Class.forName("android.app.LoadedApk");
            Field mApplicationF = loadedApkC.getDeclaredField("mApplication");
            mApplicationF.setAccessible(true);
            mApplicationF.set(loadedApkO, null);

            //旧的App
            Field oldApplicationF = activityThreadC.getDeclaredField("mInitialApplication");
            oldApplicationF.setAccessible(true);
            Application oldApplication = (Application) oldApplicationF.get(activityThreadO);
            //App list
            Field mAllApplicationsF = activityThreadC.getDeclaredField("mAllApplications");
            mAllApplicationsF.setAccessible(true);
            ArrayList<Application> mAllApplicationsO = (ArrayList<Application>) mAllApplicationsF.get(activityThreadO);

            //删除旧的
            mAllApplicationsO.remove(oldApplication);

            //
            Field mApplicationInfoFInLoadedApk = loadedApkC.getDeclaredField("mApplicationInfo");
            mApplicationInfoFInLoadedApk.setAccessible(true);
            ApplicationInfo appInfo_LoadedApk = (ApplicationInfo) mApplicationInfoFInLoadedApk.get(loadedApkO);
            Field mApplicationInfoFInAppBindData = appBindData.getDeclaredField("appInfo");
            mApplicationInfoFInAppBindData.setAccessible(true);
            ApplicationInfo appInfo_AppBindData = (ApplicationInfo) mApplicationInfoFInAppBindData.get(boundApplicationO);

            appInfo_LoadedApk.className = appClassName;
            appInfo_AppBindData.className = appClassName;

            Method makeApplicationM = loadedApkC.getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
            makeApplicationM.setAccessible(true);
            Application app = (Application) makeApplicationM.invoke(loadedApkO, false, null);

            //替换新的
            oldApplicationF.set(activityThreadO, app);
            mAllApplicationsO.add(app);

            app.onCreate();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void splitSrcApk(byte[] apkData) throws IOException {
        int ablen = apkData.length;
        byte[] dexlen = new byte[4];
        System.arraycopy(apkData, ablen - 4, dexlen, 0, 4);
        ByteArrayInputStream bis = new ByteArrayInputStream(dexlen);
        DataInputStream dis = new DataInputStream(bis);
        int readInt = dis.readInt();
        dis.close();

        Log.d(TAG, "splitSrcApk: " + readInt);
        byte[] newdex = new byte[readInt];
        System.arraycopy(apkData, ablen - 4 - readInt, newdex, 0, readInt);

        //写入apk文件
        File file = new File(apkFileName);
        try {
            FileOutputStream localFileOutputStream = new FileOutputStream(file);
            localFileOutputStream.write(newdex);
            localFileOutputStream.close();
        } catch (IOException localIOException) {
            throw new RuntimeException(localIOException);
        }

        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(
                        new FileInputStream(file)));

        while (true) {
            ZipEntry entry = zis.getNextEntry();
            if ((entry == null)) {
                zis.close();
                break;
            }

            String name = entry.getName();
            //从源程序拷贝so库到壳程序
            if (name.startsWith("lib/") && name.endsWith(".so")) {
                File storeFile = new File(libPath + "/"
                        + name.substring(name.lastIndexOf('/')));
                storeFile.createNewFile();

                FileOutputStream fos = new FileOutputStream(storeFile);
                byte[] arrayOfbytes = new byte[1024];
                while (true) {
                    int i = zis.read(arrayOfbytes);
                    if (i == -1)
                        break;
                    fos.write(arrayOfbytes, 0, i);
                }
                fos.flush();
                fos.close();
            }

            zis.closeEntry();
        }

        zis.close();

    }

    private byte[] readDexFileFromApk() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipInputStream zis;
        try {
            zis = new ZipInputStream(
                    new BufferedInputStream(
                            new FileInputStream(this.getApplicationInfo().sourceDir)));
            while (true) {
                ZipEntry entry = zis.getNextEntry();
                if ((entry == null)) {
                    zis.close();
                    break;
                }

                if (entry.getName().equals("classes.dex")) {
                    byte[] arrayOfbytes = new byte[1024];
                    while (true) {
                        int i = zis.read(arrayOfbytes);
                        if (i == -1)
                            break;
                        bos.write(arrayOfbytes, 0, i);
                    }
                }
                zis.closeEntry();
            }
            zis.close();
            return bos.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
