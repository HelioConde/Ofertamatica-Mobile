package br.primor.ocrlab;

import java.time.LocalDate;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public final class Expiry {
 private static final Pattern LABEL = Pattern.compile("(?<![A-Z])(?:VALIDADE|VENCIMENTO|VENCE|VENC|VAL)\\s*[:.\\-]?\\s*(?=\\d)");
 private static final Pattern NUM = Pattern.compile("^(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})\\s*[/.-]\\s*(\\d{4}|\\d{2})(?!\\d)");
 private static final Pattern COMPACT = Pattern.compile("^(\\d{2})(\\d{2})(\\d{4}|\\d{2})(?!\\d)");
 private static final Pattern MONTH = Pattern.compile("^(\\d{1,2})\\s+([A-Z]{3,9})\\s+(\\d{4}|\\d{2})(?!\\d)");
 private static final String[] MONTHS = {"JAN","FEV","MAR","ABR","MAI","JUN","JUL","AGO","SET","OUT","NOV","DEZ"};
 public static String read(String text) {
  String normalized = Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT);
  Set<String> dates=new HashSet<>();
  String[] lines=normalized.split("\\R");
  for(int i=0;i<lines.length;i++) {
   String line=lines[i].trim();
   // An isolated expiry label may be emitted as a separate OCR line.
   // Only join an immediately following standalone, separated date; never a bare lot number.
   if(i+1<lines.length && line.matches("(?:VALIDADE|VENCIMENTO|VENCE|VENC|VAL)\\s*[:.\\-]?")) {
    String next=lines[i+1].trim();
    if(NUM.matcher(next).matches() || MONTH.matcher(next).matches()) line=line+" "+next;
   }
   Matcher labels=LABEL.matcher(line);
   while(labels.find()) {
    String tail=line.substring(labels.end());
    Matcher m=NUM.matcher(tail);
    if(!m.find()){m=COMPACT.matcher(tail); if(!m.find()){m=MONTH.matcher(tail);if(!m.find())continue;}}
    try {
     int day=Integer.parseInt(m.group(1)),year=Integer.parseInt(m.group(3)),month;
     String mo=m.group(2);
     if(mo.matches("\\d+")) month=Integer.parseInt(mo);
     else {month=Arrays.asList(MONTHS).indexOf(mo.substring(0,3))+1;}
     if(year<100)year+=2000;
     dates.add(LocalDate.of(year,month,day).toString());
    } catch(RuntimeException ignored){}
   }
  }
  return dates.size()==1?dates.iterator().next():null;
 }
}
