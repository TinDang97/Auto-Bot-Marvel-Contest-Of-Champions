package com.trtin.autobot;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class AutoBot extends Service  {

    static boolean isStarted = false;

    static final String EXTRA_RESULT_CODE="resultCode";
    static final String EXTRA_RESULT_INTENT="resultIntent";
    static final String ACTION_PLAY = "play_game";
    static final String ACTION_PAUSE = "pause_game";
    static final String ACTION_START = "start_service";

    private int result_code;

    private NotificationManagerCompat notificationManagerCompat;
    private WindowManager wmgr;
    private ScreenCapture screenCapture;

    private Intent data;

//    private int energySmall;
//    private int energySuper;
//    private String modeE = "";
//    private int executeModeE;
//    private long energyClock = 0;

    private java.lang.Process process = null;
    private DataOutputStream STDIN = null;
    private BufferedReader reader = null;

    private String script = "";
    private String scrAttack = "";
    private String scrDef = "";
    private String scrJumpAfter = "";
    private String scrJumpBefore = "";

    private int swipeOnMap = 0;
    private int healthPercent = -1;
    private int stackAct = 0;

    private boolean onMap = false;
    private boolean isF = false;
    private boolean isLoading = false;
    private boolean isSkip = false;
    private boolean isReader = false;
    private final String waiter = "Waiting!!!";

    private Handler mHandlerReader = null;
    private Handler mHandlerMap = null;
    private Handler mHandlerSkip = null;
    private Handler mHandlerButton = null;
    private Handler mHandlerFight = null;

    private HandlerThread threadReader = null;
    private HandlerThread threadMap = null;
    private HandlerThread threadButton = null;
    private HandlerThread threadSkip = null;
    private HandlerThread threadFight = null;

    private Runnable rThreadReader = null;

    private Pixel image = null;

    @Override
    public void onCreate() {
        super.onCreate();
        isStarted = true;

        notificationManagerCompat = NotificationManagerCompat.from(this);

        screenCapture = new ScreenCapture(this);
        wmgr = (WindowManager) getSystemService(WINDOW_SERVICE);

        threadReader = new HandlerThread("Read reader", Thread.MAX_PRIORITY);
        threadMap = new HandlerThread("Process Map", Thread.NORM_PRIORITY);
        threadButton = new HandlerThread("Process Button", Thread.NORM_PRIORITY);
        threadSkip = new HandlerThread("Process Skip", Thread.NORM_PRIORITY);
        threadFight = new HandlerThread("Process Fight", Thread.NORM_PRIORITY);

        image = new Pixel();

//        BitmapFactory.Options options = new BitmapFactory.Options();
//        options.inScaled = false;
//
//        Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.concerp, options);
//        int isP = 0;

//        for(int x = 0; x < 90; x++){
//            int r = Color.red(end.getPixel(x, 12));
//            int g = Color.green(end.getPixel(x, 12));
//            int b = Color.blue(end.getPixel(x, 12));
//
//            int min = Math.min(r, Math.min(g, b));
//
//            if(min > 254)
//                isP++;
//        }
//
//        Log.i("Test", "PTrue: " + isP);

    }

    private void init(){
        threadReader.start();
        mHandlerReader = new Handler(threadReader.getLooper());

        threadMap.start();
        mHandlerMap = new Handler(threadMap.getLooper());

        threadSkip.start();
        mHandlerSkip = new Handler(threadSkip.getLooper());

        threadButton.start();
        mHandlerButton = new Handler(threadButton.getLooper());

        threadFight.start();
        mHandlerFight = new Handler(threadFight.getLooper());

        rThreadReader = new Runnable() {
            @Override
            public void run() {
                try {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if(line.equals(""))
                            continue;

                        if(line.equals("mEnd")) {
                            if(!getScript().equals("")){
                                STDIN.write(("dumpsys window windows | grep -E 'mCurrentFocus' \n").getBytes("UTF-8"));
                                STDIN.flush();
                                mHandlerReader.post(this);
                            }
                            else
                                isReader = false;

                            return;
                        }

                        if (!line.contains("com.kabam.marvelbattle")) {
                            stopSelf();
                            return;
                        }

                        try {
                            STDIN.write(String.format("%s; echo 'mEnd'\n", getScript()).getBytes("UTF-8"));
                            setScript("");
                            STDIN.flush();

                        } catch (IOException e) {
                            e.printStackTrace();
                            initNotification(false);
                            screenCapture.stopProjection();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Runnable rThreadMap = new Runnable() {
            @Override
            public void run() {
                if (isF() || isLoading() || isSkip()) {
                    synchronized (waiter){
                        Log.i(waiter, "Map");
                        try {
                            waiter.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.i(waiter, "Map end");
                    }
                    mHandlerMap.post(this);
                    return;
                }

                viewMap();
                mHandlerMap.post(this);
            }
        };

        Runnable rThreadButton = new Runnable() {
            @Override
            public void run() {
                if (isF() || isOnMap() || isSkip()) {
                    synchronized (waiter){
                        Log.i(waiter, "Button");
                        try {
                            waiter.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.i(waiter, "Button end");

                    }
                    mHandlerButton.post(this);
                    return;
                }
                selectButton();
                mHandlerButton.post(this);
            }
        };

        Runnable rThreadSkip = new Runnable() {
            @Override
            public void run() {
                if (isF() || isLoading() || isOnMap()) {
                    synchronized (waiter){
                        Log.i(waiter, "Skip");
                        try {
                            waiter.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.i(waiter, "Skip end");
                    }
                    mHandlerSkip.post(this);
                    return;
                }

                selectSkip();
                mHandlerSkip.post(this);
            }
        };

        Runnable rThreadFight = new Runnable() {
            @Override
            public void run() {
                if (isOnMap() || isSkip()) {
                    synchronized (waiter){
                        Log.i(waiter, "fight");
                        try {
                            waiter.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.i(waiter, "fight end");

                    }
                    mHandlerFight.post(this);
                    return;
                }
                AI();
                mHandlerFight.post(this);
            }
        };

        mHandlerReader.post(rThreadReader);
        mHandlerButton.post(rThreadButton);
        mHandlerMap.post(rThreadMap);
        mHandlerSkip.post(rThreadSkip);
        mHandlerFight.post(rThreadFight);

        scrAttack = getTap(1600, 540);
        scrDef = getSwipe(350, 540, 350, 540, 1000);
        scrJumpAfter = getSwipe(800, 540, 200, 540);
        scrJumpBefore = getSwipe(1300, 540, 1900, 540);

    }

    @Override
    public void onDestroy() {
        notificationManagerCompat.cancel(1);

        screenCapture.stopProjection();
        isStarted = false;
        releaseSU();

        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        threadReader.quit();
        threadButton.quit();
        threadSkip.quit();
        threadMap.quit();
        threadFight.quit();

        super.onDestroy();
    }

    private void initNotification(boolean isRunning){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        Intent intent=
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 123, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_android_black_24dp)
                .setContentTitle("AutoBot")
                .setContentText("Developed by TrungTin")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(Color.rgb(13, 104, 22))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setContentIntent(pendingIntent);

        if(isRunning)
            builder.addAction(addActionNotify(R.drawable.ic_pause_black_24dp, "Pause", ACTION_PAUSE));
        else
            builder.addAction(addActionNotify(R.drawable.ic_play_arrow_black_24dp, "Run", ACTION_PLAY));
        notificationManagerCompat.notify(1, builder.build());
    }

    private NotificationCompat.Action addActionNotify(int iconId, String title, String action){
        Intent intent = new Intent(getApplicationContext(), AutoBot.class);
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 123, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Action(iconId, title, pendingIntent);
    }

    private synchronized boolean isLoading() {
        return isLoading;
    }

    private synchronized void setLoading(boolean loading) {
        isLoading = loading;
    }

    private synchronized void setScript(String script){
        this.script = script;
    }

    private synchronized String getScript(){
        return script;
    }

    private synchronized boolean isF() {
        return isF;
    }

    private synchronized void setF(boolean f) {
        isF = f;
    }

    private synchronized  boolean isOnMap() {
        return onMap;
    }

    private synchronized  void setOnMap(boolean onMap) {
        this.onMap = onMap;
    }

    public synchronized boolean isSkip() {
        return isSkip;
    }

    public synchronized void setSkip(boolean skip) {
        isSkip = skip;
    }

    public void running(Bitmap bitmap) throws IOException, InterruptedException {
        image.setImage(bitmap);
    }

//    private void skipSpec(Bitmap img){
//        int green;
//        int blue;
//        int red;
//        boolean isTrue = true;
//        int[] x = new int[]{835,895, 312, 40};
//        for (int y = 0; y <= x[2]; y+=5)
//            for (int i = 0; i <= x[3]; i+=5){
//                green = Color.green(img.getPixel(x[0]+y, x[1]+i));
//                blue = Color.blue(img.getPixel(x[0]+y, x[1]+i));
//                red = Color.red(img.getPixel(x[0]+y, x[1]+i));
//                if (!(red == 0 && blue == 0 && green == 0)) {
//                    isTrue = false;
//                }
//            }
//
//        if (isTrue){
//            mExecute(getTap(954, 1036));
//        }
//    }

//    private boolean outOfEnergy(Bitmap img){
//        if(energySmall == 0 && energySuper == 0)
//            return false;
//
//        int green;
//        int blue;
//        int red;
//        int[] x = {857,201, 2, 26};
//        boolean isTrue = true;
//
//
//        for (int y = 0; y <= x[2]; y++)
//            for (int i = 0; i <= x[3]; i++){
//                green = Color.green(img.getPixel(x[0]+y, x[1]+i));
//                blue = Color.blue(img.getPixel(x[0]+y, x[1]+i));
//                red = Color.red(img.getPixel(x[0]+y, x[1]+i));
//                if (!(red > 140 && blue > 140 && green > 140 && Math.abs(red - green) < 25 && Math.abs(red - blue) < 25)) {
//                    isTrue = false;
//                }
//            }
//
//        if (isTrue) {
//            if(System.currentTimeMillis() - energyClock < 5000){
//                return false;
//            }
//            else
//                energyClock = System.currentTimeMillis();
//
////            Log.i("autobot123", "memory " + modeE + " - " + energySmall + " - " + energySuper);
//            switch (modeE) {
//                case "swap":
//                    if(executeModeE == 0) {
//                        if(useEsmall(img)) {
//                            executeModeE = 1;
//                            return true;
//                        }
//                    }
//                    else  {
//                        if(useEsuper(img)) {
//                            executeModeE = 0;
//                            return true;
//                        }
//                    }
//                    break;
//                case "small":
//                    return useEsmall(img);
//
//                case "super":
//                    return useEsuper(img);
//
//                case "smallF":
//                    if(energySmall > 0)
//                        useEsmall(img);
//                    else
//                        useEsuper(img);
//                    break;
//                case "superF":
//                    if(energySuper > 0)
//                        useEsuper(img);
//                    else
//                        useEsmall(img);
//                    break;
//            }
//        }
//        return false;
//    }
//
//    private boolean useEsmall(Bitmap img){
//        if (energySmall > 0)
//            if(useE(img, new int[]{946, 759, 1, 12})) {
//                energySmall--;
//                return true;
//            }
//            else if (useE(img, new int[]{726, 759, 0, 13})){
//                energySmall--;
//                return true;
//            }
//            else
//                energySmall = 0;
//        return false;
//    }
//
//    private boolean useEsuper(Bitmap img){
//        Log.i("autobot", "en " + energySuper);
//        if(energySuper > 0)
//            if(useE(img, new int[]{1182, 764, 2, 7})) {
//                energySuper--;
//                return true;
//            }
//        if(useE(img, new int[]{1181, 740, 2, 15})) {
//            return false;
//        }
//        else if (useE(img, new int[]{1347, 759, 1, 12})){
//            energySuper--;
//            return true;
//        }
//        else if (useE(img, new int[]{1352, 740, 0, 12})){
//            return false;
//        }
//        else if (useE(img, new int[]{946, 759, 1, 12})){
//            energySuper--;
//            return true;
//        }
//        else if (useE(img, new int[]{951, 740, 1, 12})){
//            return false;
//        }
//        else
//            energySuper = 0;
//        return false;
//    }
//
//    private boolean useE(Bitmap img, int[] x){
//        int green;
//        int blue;
//        int red;
//        boolean isTrue = true;
//
//        for (int y = 0; y <= x[2]; y++)
//            for (int i = 0; i <= x[3]; i++){
//                green = Color.green(img.getPixel(x[0]+y, x[1]+i));
//                blue = Color.blue(img.getPixel(x[0]+y, x[1]+i));
//                red = Color.red(img.getPixel(x[0]+y, x[1]+i));
//                if (!(red > 140 && blue > 140 && green > 140 && Math.abs(red - green) < 25 && Math.abs(red - blue) < 25)) {
//                    isTrue = false;
//                }
//            }
//
//        if (isTrue){
//            controlCmd(new String[]{"input tap " + x[0] + " " + x[1]});
////            Log.i("autobot energy", "input tap " + x[0] + " " + x[1]);
//            return true;
//        }
//        return false;
//    }

    private void selectButton(){
        int r, g, b, min, max, pTrue;
        float percent = 0;
        Point pointTrue = new Point(-1, -1);

        int[][] position = {
                {1740, 1000, 42},  //Fight button
                {915, 205, 108}, //level up
                {625,  855, 52}, //Play next - end chap
                {960, 650, 85} //Loading
        };

        for(int[] point : position){
            pTrue = 0;

            for (int y = -1; y <= 1; y++)
                for (int x = -50; x <= 50; x++){

                    g = image.getGreen(point[0] + x, point[1] + y);
                    b = image.getBlue(point[0] + x, point[1] + y);
                    r = image.getRed(point[0] + x, point[1] + y);

                    max = Math.max(r, Math.max(g, b));
                    min = Math.min(r, Math.min(g, b));

                    if (min > 240  && max - min < 3) {
                        pTrue++;
                    }

                    if(point == position[3]){
                        if (min > 220  && max - min < 20) {
                            pTrue++;
                        }
                    }

                    if(isF() || isOnMap() || isSkip())
                        return;
                }

                Log.i("Button", "Position: " + point[0] + " - " + point[1] + " is " + pTrue);

            if (((float) pTrue / point[2]) > percent){
                 percent = ((float) pTrue / point[2]);
                 pointTrue.set(point[0], point[1]);
            }
        }

        if(percent < 0.9){
            if(isLoading()) {
                setLoading(false);

                synchronized (waiter) {
                    waiter.notifyAll();
                }
            }

            return;
        }

        if(!isLoading())
            setLoading(true);

        if(pointTrue.equals(960, 650) ){
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return;
        }

        if(pointTrue.equals(625, 855)){
            Point check = new Point(785, 282);
            int isChap6 = 0;

            for(int x = 0; x < 90; x++){
                r = image.getRed(check.x + x, check.y);
                g = image.getGreen(check.x + x, check.y);
                b = image.getBlue(check.x + x, check.y);

                min = Math.min(r, Math.min(g, b));

                if(min > 254)
                    isChap6++;
            }

            if(((float) isChap6 / (float) 11) < 0.9) {

                int[] point = {925, 855, 50};

                pTrue = 0;

                for (int y = -1; y <= 1; y++)
                    for (int x = -50; x <= 50; x++){

                        g = image.getGreen(point[0] + x, point[1] + y);
                        b = image.getBlue(point[0] + x, point[1] + y);
                        r = image.getRed(point[0] + x, point[1] + y);

                        max = Math.max(r, Math.max(g, b));
                        min = Math.min(r, Math.min(g, b));

                        if (min > 240  && max - min < 3) {
                            pTrue++;
                        }

                        if(point == position[3]){
                            if (min > 220  && max - min < 20) {
                                pTrue++;
                            }
                        }
                    }

                if(((float) pTrue / (float) point[2]) > 0.9){
                    pointTrue.set(925, 855);
                }
            }
        }

        mExecute(getTap(pointTrue.x, pointTrue.y));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void selectSkip(){
        int r, g, b, min, max, pTrue;
        float percent = 0;
        Point pointTrue = new Point();

        int[][] position = {
                {960, 1008, 28}, //SKIP
                {965, 390, 10000}, //tap anywhere to continue
        };

        for(int[] point : position){
            pTrue = 0;

            for (int y = -1; y <= 1; y++)
                for (int x = -50; x <= 50; x++){

                    g = image.getGreen(point[0] + x, point[1] + y);
                    b = image.getBlue(point[0] + x, point[1] + y);
                    r = image.getRed(point[0] + x, point[1] + y);

                    max = Math.max(r, Math.max(g, b));
                    min = Math.min(r, Math.min(g, b));

                    if (min > 100  && max - min < 5) {
                        pTrue++;
                    }

                    if(isF() || isLoading() || isOnMap())
                        return;
                }
            Log.i("Skip", "Position: " + point[0] + " - " + point[1] + " is " + pTrue);
            if (((float) pTrue / point[2]) > percent){
                percent = ((float) pTrue / point[2]);
                pointTrue.set(point[0], point[1]);
            }
        }

        if(percent < 0.9){
            if(isSkip()) {
                setSkip(false);

                synchronized (waiter) {
                    waiter.notifyAll();
                }
            }

            return;
        }

        if(!isLoading())
            setSkip(true);

        mExecute(getTap(pointTrue.x, pointTrue.y));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }


//    private boolean selectSkip(){
//        int green;
//        int blue;
//        int red;
//
//        int isTrue;
//        int max, min;
//
//
////        int[][] position = {{1715,994,0,15},  {745, 1006, 12, 0}, {728, 939, 0, 14}, {973, 998, 0, 22}, {1142, 910, 0, 12},
////                {1065, 701, 0, 16}, {965, 607, 0, 15}, {820, 1037}, {993, 781, 0, 17},
////                {581, 846, 0, 15}, {745, 1026, 0, 10}, {1052, 848, 1, 15}};
//        int[][] position = {{960, 995, 272}, //SKIP
//                {1740, 1000, 330},  //Fight button
//                {965, 390, 838}, //tap anywhere to continue
//                {915, 205, 1187}, //level up
//                {965,  855, 442} //Play next - end chap
//        };
//
//        for (int[] po : position){
//            isTrue = 0;
//            for (int y = -20; y <= 20; y++)
//                for (int x = -40; x <= 40; x++){
//
//                    green = image.getGreen(po[0] + x, po[1] + y);
//                    blue = image.getBlue(po[0] + x, po[1] + y);
//                    red = image.getRed(po[0] + x, po[1] + y);
//
//                    max = Math.max(red, Math.max(green, blue));
//                    min = Math.min(red, Math.min(green, blue));
//
//                    if (min > 200  && max - min < 10) {
//                        isTrue++;
//                    }
//                }
////            Log.i("tessss", "Po: " + po[0] + "-" + po[1] +" isTrue " +isTrue);
//            if (Math.abs(isTrue - po[2]) < 50){
//
////                Log.i("tessss", "Po: " + po[0] + "-" + po[1] +" isTrue " +isTrue);
////                if (po == position[11]){
////                    boolean ended = true;
////
////                    BitmapFactory.Options options = new BitmapFactory.Options();
////                    options.inScaled = false;
////
////                    Bitmap end = BitmapFactory.decodeResource(getResources(), R.drawable.concerp, options);
////                    Bitmap compare = Bitmap.createBitmap(img, 785, 270, 91, 38);
////
////                    for (int w = 0 ; w < compare.getWidth(); w++)
////                        for (int h = 0; h < compare.getHeight(); h++){
////                            int redE = Color.red(end.getPixel(w,h));
////                            int blueE = Color.blue(end.getPixel(w,h));
////                            int greenE = Color.green(end.getPixel(w,h));
////                            int redC = Color.red(compare.getPixel(w,h));
////                            int blueC = Color.blue(compare.getPixel(w,h));
////                            int greenC = Color.green(compare.getPixel(w,h));
////
////                            if (Math.abs(redE - redC) > 5
////                                    && Math.abs(blueE - blueC) > 5
////                                    && Math.abs(greenE - greenC) > 5
////                                    ) {
////                                ended = false;
////                                break;
////                            }
////                        }
////
////                    if (ended) {
////                        tap(581, 846);
////                        return true;
////                    }
////                }
////
////                if(po == position[9]){
////                    tap(1051, 848);
////                    return true;
////                }
//
//                mExecute(getTap(po[0], po[1]));
//                try {
//                    Thread.sleep(2000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                return true;
//            }
//        }
//
//        return false;
//    }

    private void AI(){

        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        boolean check = true;

        int x = 960;
        int red, green, blue;

        for(int y = 50; y <= 60; y+=2){

            green = image.getGreen(x, y);
            blue = image.getBlue(x, y);
            red = image.getRed(x, y);

            if(!(Math.abs(green - red) > 50 && Math.abs(green - blue) > 50 && green > 70)) {
                check = false;
                break;
            }
        }

        if (!check) {
            if(isF()) {
                healthPercent = 0;
                setF(false);

                for (int i = 0; i < 3; i++) {
                    mExecute(getTap(1600, 540));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                synchronized (waiter) {
                    waiter.notifyAll();
                }
            }
            return;
        }

        Log.i("Sdasda", "AI run : " + stackAct);

        setF(true);

        int currHp = 0;
        int max, min;

        int y = 92;
        for (x = 365; y <= 445; y++){
            green = image.getGreen(x, y);
            blue = image.getBlue(x, y);
            red = image.getRed(x, y);

            max = Math.max(red, Math.max(green, blue));
            min = Math.min(red, Math.min(green, blue));

            if(max - min < 3 && min > 240)
                currHp++;
        }

        if(healthPercent == -1){
            healthPercent = currHp;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return;
        }

        if(currHp != healthPercent){
            mExecute(scrDef);
            stackAct = 0;
            healthPercent = currHp;
            return;
        }
        else {
            stackAct++;
        }

       if(stackAct <= 10){
            mExecute(scrJumpAfter);
            return;
       }

       if(stackAct <= 20){
           mExecute(scrJumpBefore);
           return;
       }


       mExecute(scrAttack);

       if(stackAct > 60)
           stackAct = 0;

        green = image.getGreen(285, 930);
        red = image.getRed(285, 930);
        blue = image.getBlue(285, 930);

       if(Math.abs(green - 145) < 3 && Math.abs(red - 53) < 3 && Math.abs(blue - 43) < 3)
           mExecute(getTap(285, 930));


    }

    private boolean checkMap(){
        int green;
        int blue;
        int red;

        for (int y = -100; y <= 350; y+=2)
            for (int x = 100; x >= -576; x-=2) {
                green = image.getGreen(x + 960, y + 540);
                blue = image.getBlue(x + 960, y + 540);
                red = image.getRed(x + 960, y + 540);

                if (green == 204 && blue == 204 && red == 0) {
                    if (isPointMapGone(new Point(960 + x, 540 + y))) {
                        return true;
                    }
                    else {
                        x+= 30;
                    }
                }

                if(isF() || isLoading() || isSkip()) {
                    return false;
                }
            }
        return false;
    }

    private boolean isPointMapGone(Point point){
        int green;
        int blue;
        int red;

        int isTrue = 0;
        for (int x = 0; x >= -10; x--)
            for (int y = 0; y <= 15; y++){
            green = image.getGreen(point.x + x, point.y + y);
            blue = image.getBlue(point.x + x, point.y + y);
            red = image.getRed(point.x + x, point.y + y);

            if (green == 204 && blue == 204 && red == 0){
                isTrue++;
            }
        }
        return isTrue > 55  && isTrue < 70;
    }

    private boolean isPointMap(Point point){
        int green;
        int blue;
        int red;

        int isTrue = 0;

        for (int x = 0; x <= 15; x++)
            for (int y = -10; y <= 0; y++){
            green = image.getGreen(point.x + x, point.y + y);
            blue = image.getBlue(point.x + x, point.y + y);
            red = image.getRed(point.x + x, point.y + y);

            if (red == 0 && blue == 0 && green > 10){
                isTrue++;
            }
        }
//        Log.i("check point gone!", "is: " + isTrue + " - " + point.x + " " + point.y);
        return isTrue >= 100;
    }

    private void selectMap(){
        switch (swipeOnMap){
            case 0:
                break;
            case 1:
                mExecute(getSwipe(960, 540, 960, 840));
                break;
            case 2:
                mExecute(getSwipe(960, 540, 960, 240));
                break;
            case 3:
                mExecute(getSwipe(960, 540, 660, 540));
                break;
            case 4:
                mExecute(getSwipe(960, 540, 1260, 540));
                break;
            default:
                break;
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        swipeOnMap++;

        if(swipeOnMap > 4) {
            swipeOnMap = 0;
            return;
        }

        int green;
        int blue;
        int red;


        for (int y = 150; y >= -250; y-=2)
            for (int x = -500; x <= 700; x+=2){

            green = image.getGreen(x + 960, y + 540);
            blue = image.getBlue(x + 960, y + 540);
            red = image.getRed(x + 960, y + 540);

            if (red == 0 && blue == 0 && green > 10) {
                if (isPointMap(new Point(x + 960, y + 540))) {
                    mExecute(getTap(x + 960 + 15, y + 540 + 20));
                    swipeOnMap = 0;
                    return;
                }
                else {
                    x+=30;
                }
            }

            if(isF() || isLoading() || isSkip())
                return;
        }

        selectMap();
    }

    private void viewMap(){

        if(!checkMap()){
            if(isOnMap()) {
                setOnMap(false);

                synchronized (waiter) {
                    waiter.notifyAll();
                }
            }

            return;
        }

        if (!isOnMap())
            setOnMap(true);

        Log.i("tessss","check map true!!!!!!");
        selectMap();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        int green;
//        int blue;
//        int red;
//
//            for (int y = -324; y <= 324; y++)
//                for (int x = -576; x <= 576; x++) {
//                    green = Color.green(img.getPixel(960 + x, 540 + y));
//                    blue = Color.blue(img.getPixel(960 + x, 540 + y));
//                    red = Color.red(img.getPixel(960 + x, 540 + y));
//
//                    if (red == 0 && blue == 0 && green > 10) {
//                        if (isPointMap(img, new Point(x + 960, y + 540))) {
//                            mExecute(getTap(x + 960 + 15, y + 540 + 15));
//                            return true;
//                        }
//                    }
//                }
//
//            mExecute(getSwipe(960, 540, 960, 840));
//            Log.i("tessss", "searched haven't point!");


//        int[][] position = new int[][]{{1305, 535}, {630, 536}, {1124, 420},
//                {794, 420}, {1126, 653}, {794, 654}, {960, 304}, {960, 774},
//                {794, 302}, {956, 66}, {1291, 302}, {960, 185}, {1209, 360},
//                {1623, 538}, {1457, 537}, {625, 415}, {461,537}};
//
//        for (int[] x : position){
//
//            boolean isTrue = true;
////            for (int y = -1; y < 2; y++)
////                for (int i = -1; i < 2; i++){
////                    green = Color.green(img.getPixel(x[0]+y, x[1]+i));
////                    blue = Color.blue(img.getPixel(x[0]+y, x[1]+i));
////                    red = Color.red(img.getPixel(x[0]+y, x[1]+i));
////                    if (!(red <= 5 && blue<=5 && green > 100)) {
////                        isTrue = false;
////                    }
////                }
//
//                green = Color.green(img.getPixel(x[0], x[1]));
//                    blue = Color.blue(img.getPixel(x[0], x[1]));
//                    red = Color.red(img.getPixel(x[0], x[1]));
//                    if (!(red <= 5 && blue <= 5 && green > 100)) {
//                        isTrue = false;
//                    }


//            if (isTrue){
//                //Log.i("tessss", String.valueOf(green));
//                if(x == position[8]){
//                    swipe(x[0], x[1]/2, x[0], x[1]/2+100);
//                    tap(x[0], x[1]+100);
//                    return;
//                }
//                tap(x[0], x[1]);
////                Log.i("autobot chap", "input tap " + x[0] + " " + x[1]);
//                return;
//            }
//        }
    }

    private synchronized String getTap(int x, int y){
        x *= screenCapture.xRatio;
        y *= screenCapture.yRatio;

        return "input tap " + x + " " + y;
    }

    private synchronized String getSwipe(int xStart, int yStart, int xEnd, int yEnd, int time){
        xStart *= screenCapture.xRatio;
        yStart *= screenCapture.yRatio;

        xEnd *= screenCapture.xRatio;
        yEnd *= screenCapture.yRatio;

        return "input swipe " + xStart + " " + yStart + " " + xEnd + " " + yEnd + " " + time;
    }

    private synchronized String getSwipe(int xStart, int yStart, int xEnd, int yEnd){
       return getSwipe(xStart, yStart, xEnd, yEnd, 200);
    }

    private boolean isRunning() {
        if (process == null) {
            return false;
        }
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            // if this is thrown, we're still running
        }
        return true;
    }

    private synchronized void mExecute (String cmd) {

        setScript(cmd);

        if(isReader)
            return;

        try {
            STDIN.write(("dumpsys window windows | grep -E 'mCurrentFocus' \n").getBytes("UTF-8"));
            isReader = true;

            STDIN.flush();
            mHandlerReader.post(rThreadReader);
        }
        catch (IOException e) {
            e.printStackTrace();
            initNotification(false);
            screenCapture.stopProjection();
        }
    }

    private void  initSU(){
        try {
            process = Runtime.getRuntime().exec("su");
            STDIN = new DataOutputStream(process.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void releaseSU(){
        if(isRunning())
        try {
            STDIN.write("exit\n".getBytes("UTF-8"));
            STDIN.flush();
            STDIN.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                if( intent.getAction() != null)
                switch (intent.getAction()) {
                    case ACTION_PLAY:
                        if (!isRunning())
                            stopSelf();
                        initNotification(true);
                        screenCapture.init(result_code, data);
                        init();
                        break;
                    case ACTION_PAUSE:
                        initNotification(false);
                        screenCapture.stopProjection();
                        break;
                    case ACTION_START:

                        initNotification(false);
                        result_code = intent.getIntExtra(EXTRA_RESULT_CODE, 100);
                        data = intent.getParcelableExtra(EXTRA_RESULT_INTENT);
//                        energySmall = intent.getIntExtra("smallE", 0);
//                        energySuper = intent.getIntExtra("superE", 0);
//                        modeE = intent.getStringExtra("mode");
//                        executeModeE = intent.getIntExtra("executeModeE", 0);
                        initSU();
                        if(!isRunning()){
                            stopSelf();
                        }
                        break;
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public WindowManager getWindowManager(){
        return wmgr;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
