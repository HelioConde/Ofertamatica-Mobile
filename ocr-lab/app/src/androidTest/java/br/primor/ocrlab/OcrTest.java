package br.primor.ocrlab;
import android.content.Context;
import android.graphics.*;
import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OcrTest {
 private static final String[] EXPECTED={"2027-06-30","2027-04-24","2027-01-21","2027-05-15","2027-09-28","2027-08-20","2027-06-08","2027-05-12"};
 private static final String[][] LINES={
 {"FAB: 01/07/26","VAL: 30/06/27","LOT: 07/26 OP5"},
 {"VAL 240427","LOT 2409261"},
 {"FAB21/07/26","VAL21/01/27"},
 {"L06 M35 150526 A","VENCE: 15/05/27"},
 {"FAB: 29 SET 26","VAL: 28 SET 27"},
 {"FAB: 20/08/2026","VAL: 20/08/2027"},
 {"FAB: 15 DEZ 25","VAL: 08 JUN 27"},
 {"L:VS19726622P3 16:46","VAL:12/05/27"}};
 private Bitmap synthetic(int i){
  Bitmap b=Bitmap.createBitmap(960,300,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  int bg=i==7?Color.BLACK:(i==1||i==6?0xffffc83c:Color.WHITE);
  int fg=i==7?Color.WHITE:Color.BLACK;
  c.drawColor(bg);p.setColor(fg);p.setTextSize(58);p.setTypeface(Typeface.create("monospace",Typeface.NORMAL));
  for(int j=0;j<LINES[i].length;j++)c.drawText(LINES[i][j],30,75+j*75,p);
  if(i==6){
   // Approximate dotted industrial ink by sampling the text mask.
   Bitmap dots=Bitmap.createBitmap(960,300,Bitmap.Config.ARGB_8888);
   Canvas d=new Canvas(dots);d.drawColor(bg);
   p.setColor(fg);
   for(int y=3;y<300;y+=6)for(int x=3;x<960;x+=6)
    if(Color.red(b.getPixel(x,y))<80)d.drawCircle(x,y,1.8f,p);
   b.recycle();b=dots;
  }
  if(i==2){Matrix m=new Matrix();m.postRotate(90);Bitmap r=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);b.recycle();return r;}
  return b;
 }
 // Experimental fallback: dark-ink mask and a small dilation to join dotted strokes.
 // No expected date is used to select a candidate.
 private Bitmap joinDarkInk(Bitmap source){
  int w=source.getWidth(),h=source.getHeight();
  int[] original=new int[w*h],out=new int[w*h];
  source.getPixels(original,0,w,0,0,w,h);
  boolean[] ink=new boolean[w*h];
  for(int i=0;i<ink.length;i++){
   int p=original[i];
   ink[i]=(77*Color.red(p)+150*Color.green(p)+29*Color.blue(p))/256<110;
  }
  Arrays.fill(out,Color.WHITE);
  int radius=Math.max(1,Math.min(3,Math.round(w/480f)));
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(ink[y*w+x])
   for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++)
    if(dx*dx+dy*dy<=radius*radius && x+dx>=0 && x+dx<w && y+dy>=0 && y+dy<h)out[(y+dy)*w+x+dx]=Color.BLACK;
  return Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888);
 }
 private String runWithFallback(TextRecognizer ocr,Bitmap image,int rotation,String label,String expected) throws Exception{
  String status=run(ocr,image,rotation,label+"-raw",expected);
  if(!"NO_READ".equals(status))return status;
  Bitmap treated=joinDarkInk(image);
  try{return run(ocr,treated,rotation,label+"-dark-ink",expected);}
  finally{treated.recycle();}
 }
 private String run(TextRecognizer ocr,Bitmap image,int rotation,String label,String expected) throws Exception{
  long start=android.os.SystemClock.elapsedRealtime();
  String text=Tasks.await(ocr.process(InputImage.fromBitmap(image,rotation)),20,TimeUnit.SECONDS).getText();
  long ms=android.os.SystemClock.elapsedRealtime()-start;
  String found=Expiry.read(text);
  String status=expected.equals(found)?"OK":found==null?"NO_READ":"WRONG_DATE";
  Log.i("OCRLab",label+" expected="+expected+" actual="+found+" status="+status+" latency_ms="+ms+" text="+text.replace("\n"," | "));
  return status;
 }
 @Test public void syntheticImages() throws Exception{
  TextRecognizer ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  int ok=0,wrong=0;
  try{
   for(int i=0;i<8;i++){
    Bitmap b=synthetic(i);
    try {String s=runWithFallback(ocr,b,i==2?270:0,"synthetic-"+(i+1),EXPECTED[i]);if("OK".equals(s))ok++;else if("WRONG_DATE".equals(s))wrong++;}
    finally{b.recycle();}
   }
  }finally{ocr.close();}
  Log.i("OCRLab","synthetic total=8 correct="+ok+" wrong="+wrong+" no_read="+(8-ok-wrong));
  assertEquals("No wrong dates permitted",0,wrong);
  assertTrue("At least six of eight synthetic samples",ok>=6);
 }
 @Test public void realPhotosIfPresent() throws Exception{
  Context ctx=InstrumentationRegistry.getInstrumentation().getContext();
  Set<String> names=new HashSet<>(Arrays.asList(ctx.getAssets().list("")));
  if(!names.contains("01.jpg")){Log.i("OCRLab","REAL PHOTOS NOT RUN: fixture zip missing");org.junit.Assume.assumeTrue("No photo fixture supplied",false);}
  TextRecognizer ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  int ok=0,wrong=0;
  try{
   for(int i=0;i<8;i++){
    String name=String.format(Locale.ROOT,"%02d.jpg",i+1);
    try(InputStream in=ctx.getAssets().open(name)){
     Bitmap b=BitmapFactory.decodeStream(in);assertNotNull(b);
     try{String s=runWithFallback(ocr,b,0,"real-"+name,EXPECTED[i]);if("OK".equals(s))ok++;else if("WRONG_DATE".equals(s))wrong++;}
     finally{b.recycle();}
    }
   }
  }finally{ocr.close();}
  Log.i("OCRLab","real total=8 correct="+ok+" wrong="+wrong+" no_read="+(8-ok-wrong));
  assertEquals("No wrong dates permitted",0,wrong);
  assertEquals("All eight examples required for initial photo acceptance",8,ok);
 }
}
