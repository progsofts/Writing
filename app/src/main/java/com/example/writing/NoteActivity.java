package com.example.writing;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Environment;
//import android.util.Log;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class NoteActivity extends Activity implements View.OnClickListener{
    private String ff;
    private String f;
    private final static float DESIGN_WIDTH = 750; //绘制页面时参照的设计图宽度
    private int select_paint_size_index = 2;
    private int select_paint_color_index = 0;
    private final GetData getData=new GetData();
    private String path2,path3;
    private NoteView fvFont;
    private static final List<Bitmap> listBitMap= new ArrayList<>();
    private final List<List<List<WordPoint>>>  listAllPoint= new ArrayList<>();
    private List<Han> list_han= new ArrayList<>();

    public ArrayList<String> results= new ArrayList<>();

    //写在成员变量中
    //这个是在活动中一些多线程交互，例如网络请求，存储请求，的回调处理
    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                //暂定0x11为手写识别结果成功
                case 0x11:
                    //String s = (String) msg.obj;\
                    StringBuilder sb=new StringBuilder();
                    for(String v:results)
                    {
                        //因为是用于关键词提取，所以我们就把同一张图片的所有结果都用一句话存起来就好了
                        sb.append(v);
                    }

                    String output= sb.toString();
                    Toast.makeText(getApplicationContext(), "识别结果："+output, Toast.LENGTH_SHORT).show();
                    Han han=(Han)msg.obj;

                    //本来以为直接把识别结果存在name里就很美好，结果不行，因为在转换的过程中，中文字符会变成乱码
                    //2020-9-10更新，这个突然就可以了，在文件中正常显示
                    han.setName(output);

                    //这部分是原来完成按钮保存文件的部分
                    list_han.add(han);
                    saveToLocal(f);

                    //把识别结果保存在本地文件，注意，这个并没有解决存在同一个文件的问题，因此分享结果并不能分享文字识别的结果
                    File file = new File(path2, han.getId()+".txt");
                    OutputStream out;
                    try {

                        out = new FileOutputStream(file);
                        out.write(output.getBytes(StandardCharsets.UTF_8));
                        out.close();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    break;
                case 0x12:
                    //String ss = (String) msg.obj;

                    break;
            }


        }
    };


    public void onCreate(Bundle s){
        super.onCreate(s);
        resetDensity();//注意不要漏掉
        setContentView(R.layout.note);
        initData();
        initUI();
        initData1();
    }
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resetDensity();//这个方法重写也是很有必要的
    }
    private void resetDensity(){
        Point size = new Point();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getSize(size);
        getResources().getDisplayMetrics().xdpi = size.x/DESIGN_WIDTH*72f;
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    private void initUI(){
        //标题框
        Button btnComplete = (Button) findViewById(R.id.btnComplete);
        Button btnReWrite = (Button) findViewById(R.id.btnReWrite);
        Button reback = (Button) findViewById(R.id.btnReBack);
        ImageView setting = (ImageView) findViewById(R.id.setting);
        btnComplete.setOnClickListener(this);
        btnReWrite.setOnClickListener(this);
        reback.setOnClickListener(this);
        setting.setOnClickListener(this);
        fvFont=(NoteView)findViewById(R.id.fvFont);

    }

    //弹出画笔颜色选项对话框
    private void showPaintColorDialog(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this,R.style.custom_dialog);
        alertDialogBuilder.setTitle("选择画笔颜色：");
        alertDialogBuilder.setSingleChoiceItems(R.array.paintcolor, select_paint_color_index, (dialog, which) -> {
            select_paint_color_index = which;
            fvFont.selectPaintColor(which);
            dialog.dismiss();
        });
        alertDialogBuilder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        alertDialogBuilder.create().show();
    }
    //弹出画笔设置选项对话框
    private void showSetDialog(){
        final String[] set = new String[] { "字体大小","字体颜色" };
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this,R.style.custom_dialog);
        alertDialogBuilder.setTitle("选择设置：");
        alertDialogBuilder.setSingleChoiceItems(set, -1, (dialog, which) -> {
            if(which==1){
                showPaintColorDialog();
            }else if(which==0){
                showPaintSizeDialog();
            }
            dialog.dismiss();
        });
        alertDialogBuilder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        alertDialogBuilder.create().show();
    }

    //弹出画笔大小选项对话框
    private void showPaintSizeDialog(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this,R.style.custom_dialog);
        alertDialogBuilder.setTitle("选择画笔大小：");
        alertDialogBuilder.setSingleChoiceItems(R.array.paintsize, select_paint_size_index, (dialog, which) -> {
            select_paint_size_index = which;
            fvFont.selectPaintSize(which);
            dialog.dismiss();
        });
        alertDialogBuilder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        alertDialogBuilder.create().show();
    }
    //创建文件夹
    private void initData() {
        //如果手机有sd卡
        try {
            String path = Environment.getExternalStorageDirectory()
                    .getCanonicalPath()
                    + "/Writing";
            File files = new File(path);
            if (!files.exists()) {
                //如果有没有文件夹就创建文件夹
                files.mkdir();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            path2 = Environment.getExternalStorageDirectory()
                    .getCanonicalPath()
                    + "/Writing/Picture";
            File file1 = new File(path2);
            if (!file1.exists()) {
                //如果有没有文件夹就创建文件夹
                file1.mkdir();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            path3 = Environment.getExternalStorageDirectory()
                    .getCanonicalPath()
                    + "/Writing/PointData";
            File file2 = new File(path3);
            if (!file2.exists()) {
                //如果有没有文件夹就创建文件夹
                file2.mkdir();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private  void initData1(){
        SharedPreferences sp = this.getSharedPreferences("userInfo", Context.MODE_PRIVATE);
        String account = sp.getString("USER_NAME", "");
        String password = sp.getString("PASSWORD", "");
        String filename = sp.getString("FILENAME", "");
        f= account + "-" + password + "-" + filename +".txt";
        try {
            ff = Environment.getExternalStorageDirectory()
                    .getCanonicalPath()
                    + "/Writing/PointData/" + account + "-" + password + "-" + filename +".txt";
        }catch (Exception ignored){}
        String ss="";
        String s=getData.readJsonFile(ff,ss);
        Gson gson1=new Gson();
        List<Han> statusLs = gson1.fromJson(s, new TypeToken<List<Han>>(){}.getType());
        Log.d("MainActivity","li="+statusLs);
        if(statusLs!=null){
            list_han=statusLs;
            for (int i=0;i<statusLs.size();i++ ){
                listAllPoint.add(statusLs.get(i).getLists());
            }
        }

    }
    //写到本地文件夹
    private void saveToLocal(String fileName) {
        //文件名
       // String fileName ="config.txt";
        try {
            //文件夹路径
            File dir = new File(path3);
            //文件夹不存在和传入的value值为1时，才允许进入创建
            Gson gson = new Gson();
            String str = gson.toJson(list_han);
            File file = new File(dir, fileName);
            OutputStream out = new FileOutputStream(file);
            out.write(str.getBytes(StandardCharsets.UTF_8));
            out.close();
            file.getPath();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void saveToSystemGallery(Bitmap bmp, long filename) {
        // 首先保存图片
        File appDir = new File(path2);
        if (!appDir.exists()) {
            appDir.mkdir();
        }
        String fileName = filename+ ".jpg";
        File file = new File(appDir, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v)
    {
        //完成按钮
        if (v.getId() == R.id.btnComplete) {
                if(fvFont.canSave) {
                fvFont.canSave=false;
                //btnComplete.setEnabled(false);
                if (fvFont.listPointB.size() > 0) {
                   // Log.d("W", "fv=" + fvFont.listPoint);
                    List<List<WordPoint>> itemPointList = new ArrayList<>();
                    List<List<Integer>> cL = new ArrayList<>();

                    for (List<WordPoint> listP : fvFont.listPointB) {
                        List<WordPoint> pointList = new ArrayList<>(listP);
                        itemPointList.add(pointList);
                    }
                    for(List<Integer>c:fvFont.colorB){
                        List<Integer> cc = new ArrayList<>(c);
                        cL.add(cc);
                    }
                    listAllPoint.add(itemPointList);
                    Bitmap bitmap = viewToBitmap(fvFont, fvFont.getWidth(), fvFont.getHeight());
                    listBitMap.add(viewToBitmap(fvFont, fvFont.getWidth(), fvFont.getHeight()));
                    Han han = new Han();
                    long id_han = new Date().getTime();
                    han.setId(id_han);
                    Date curDate = new Date(System.currentTimeMillis());
                    han.setTime(curDate);
                    han.setBitmap(path2 + "/" + id_han + ".jpg");
                    han.setLists(itemPointList);
                    han.setColor(cL);

                    saveToSystemGallery(bitmap, id_han);
                   // fvFont.colors.clear();

                    //之前保存的部分需要等待手写识别结果出来，才能保存
                    //手写文字识别测试
                    results.clear();
                    new Thread(() -> {
                        Message message = handler.obtainMessage();
                        message.what = 0x11;
                        message.obj=han;
                        // 发消息通知主线程更新UI
                        handler.sendMessage(message);
                    }).start();



                }
            }else Toast.makeText(this, "请写字！", Toast.LENGTH_SHORT).show();

            //Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fenmian);

        }

        //重写按钮
        if(v.getId()==R.id.btnReWrite)
        {
            ReWrite();
        }

        //设置
        else if(v.getId()==R.id.setting){
            showSetDialog();
        }

        //回放
        else if(v.getId()==R.id.btnReBack){
            if(list_han.size()>0) {
                //保存图片到本地
                //跳到回放界面
                Intent intent = new Intent(NoteActivity.this, Re.class);
                startActivity(intent);
            }else
                Toast.makeText(NoteActivity.this,"你都没写，哪有回放啊！",Toast.LENGTH_SHORT).show();
        }
    }

    //重写
    private void ReWrite()
    {
        fvFont.listPointB.clear();
        fvFont.mPointsB.clear();
        fvFont.colorB.clear();
        fvFont.mColor.clear();
        fvFont.mColor=new ArrayList<>();
        fvFont.colorB=new ArrayList<>();
        fvFont.mPointsB=new ArrayList<>();
        fvFont.listPointB= new ArrayList<>();
        if(fvFont.colors.size()>1) {
            fvFont.currentColor = fvFont.colors.get(fvFont.colors.size() - 1);
        }
        fvFont.colors.clear();
        fvFont.invalidate();
        if (fvFont.cacheCanvas != null) {
            fvFont.cacheCanvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            fvFont.invalidate();
        }
    }
    //生成位图
    private Bitmap viewToBitmap(View view, int bitmapWidth, int bitmapHeight){
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}