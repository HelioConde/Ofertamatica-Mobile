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
 // Morphological closing connects dotted strokes without retaining all the extra ink of dilation.
 private boolean[] morph(boolean[] source,int w,int h,int radius,boolean dilation){
  int stride=w+1; int[] integral=new int[stride*(h+1)];
  for(int y=0;y<h;y++){
   int sum=0;
   for(int x=0;x<w;x++){if(source[y*w+x])sum++;integral[(y+1)*stride+x+1]=integral[y*stride+x+1]+sum;}
  }
  boolean[] result=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){
   int left=Math.max(0,x-radius),right=Math.min(w,x+radius+1);
   int top=Math.max(0,y-radius),bottom=Math.min(h,y+radius+1);
   int sum=integral[bottom*stride+right]-integral[top*stride+right]-integral[bottom*stride+left]+integral[top*stride+left];
   result[y*w+x]=dilation?sum>0:sum==(right-left)*(bottom-top);
  }
  return result;
 }
 private Bitmap prepareInk(Bitmap source,int threshold,int radius,boolean close,boolean invert){
  // Bound the processing size. Scale up again only after the binary mask is produced.
  int max=Math.max(source.getWidth(),source.getHeight());
  Bitmap bounded=max>1600?Bitmap.createScaledBitmap(source,Math.max(1,source.getWidth()*1600/max),Math.max(1,source.getHeight()*1600/max),true):source;
  int w=bounded.getWidth(),h=bounded.getHeight();int[] pixels=new int[w*h];
  bounded.getPixels(pixels,0,w,0,0,w,h);if(bounded!=source)bounded.recycle();
  boolean[] ink=new boolean[w*h];for(int i=0;i<ink.length;i++){
   int p=pixels[i],gray=(77*Color.red(p)+150*Color.green(p)+29*Color.blue(p))/256;
   ink[i]=invert?gray>255-threshold:gray<threshold;
  }
  ink=morph(ink,w,h,radius,true);if(close)ink=morph(ink,w,h,radius,false);
  for(int i=0;i<ink.length;i++)pixels[i]=ink[i]?Color.BLACK:Color.WHITE;
  Bitmap b=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
  if(max<1200){Bitmap enlarged=Bitmap.createScaledBitmap(b,w*2,h*2,true);b.recycle();return enlarged;}
  return b;
 }
 private String read(TextRecognizer ocr,Bitmap image,int rotation,String label) throws Exception{
  long start=android.os.SystemClock.elapsedRealtime();
  String text=Tasks.await(ocr.process(InputImage.fromBitmap(image,rotation)),20,TimeUnit.SECONDS).getText();
  long ms=android.os.SystemClock.elapsedRealtime()-start;
  String found=Expiry.read(text);
  Log.i("OCRLab",label+" parsed="+found+" latency_ms="+ms+" text="+text.replace("\n"," | "));
  return found;
 }
 private String runWithFallback(TextRecognizer ocr,Bitmap image,int rotation,String label,String expected) throws Exception{
  long start=android.os.SystemClock.elapsedRealtime();
  String found=read(ocr,image,rotation,label+"-raw");
  if(found==null){
   // Always evaluate the same fixed variants. No expected date is consulted until scoring.
   int r=Math.max(1,Math.min(4,Math.round(Math.max(image.getWidth(),image.getHeight())/320f)));
   int[][] variants={{110,r,1,0},{145,r+1,1,0},{165,r,0,0},{110,r,1,1}};
   Map<String,Integer> votes=new HashMap<>();
   for(int i=0;i<variants.length;i++){
    int[] v=variants[i];Bitmap b=prepareInk(image,v[0],v[1],v[2]==1,v[3]==1);
    try{String candidate=read(ocr,b,rotation,label+"-variant-"+(i+1));if(candidate!=null)votes.put(candidate,votes.getOrDefault(candidate,0)+1);}
    finally{b.recycle();}
   }
   if(votes.size()==1){Map.Entry<String,Integer> candidate=votes.entrySet().iterator().next();if(candidate.getValue()>=2)found=candidate.getKey();}
   Log.i("OCRLab",label+" fallback_votes="+votes+" (two matching variants required)");
  }
  String status=expected==null?(found==null?"OK":"WRONG_DATE"):expected.equals(found)?"OK":found==null?"NO_READ":"WRONG_DATE";
  Log.i("OCRLab",label+" FINAL expected="+expected+" actual="+found+" status="+status+" total_ms="+(android.os.SystemClock.elapsedRealtime()-start));
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
  assertTrue("Preserve validated baseline: at least seven of eight synthetic samples",ok>=7);
 }
 @Test public void noExpiryNegativeImages() throws Exception{
  TextRecognizer ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  String[][] samples={{"FAB: 15/05/27","LOT: 240427"},{"VAL: 31/02/27"},{"VAL: 15/05/27","VAL: 12/05/27"},{"LOTE: 240427","15/05/27"}};
  try{
   for(int i=0;i<samples.length;i++){
    Bitmap b=Bitmap.createBitmap(960,300,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(58);p.setColor(Color.BLACK);
    for(int j=0;j<samples[i].length;j++)c.drawText(samples[i][j],30,75+75*j,p);
    try{assertEquals("Negative sample "+i,"OK",runWithFallback(ocr,b,0,"negative-"+(i+1),null));}finally{b.recycle();}
   }
  }finally{ocr.close();}
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
