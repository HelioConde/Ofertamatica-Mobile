package br.primor.ocrlab;

import java.time.LocalDate;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public final class Expiry {
 private static final Pattern LABEL = Pattern.compile("(?<![A-Z0-9])(?:VALIDADE|VENCIMENTO|VENCE|VENC|VAL)\\s*[:.\\-]?\\s*(?![A-Z])");
 private static final Pattern NUM = Pattern.compile("^(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})\\s*[/.-]\\s*(\\d{4}|\\d{2})(?![A-Z0-9])");
 private static final Pattern COMPACT = Pattern.compile("^(\\d{2})(\\d{2})(\\d{4}|\\d{2})(?![A-Z0-9])");
 private static final Pattern MONTH = Pattern.compile("^(\\d{1,2})\\s+([A-Z]{3,9})\\s+(\\d{4}|\\d{2})(?![A-Z0-9])");
 private static final String[] MONTHS = {"JAN","FEV","MAR","ABR","MAI","JUN","JUL","AGO","SET","OUT","NOV","DEZ"};
 private static final class Result {
  final Set<String> dates=new HashSet<>(); int labels=0,invalid=0;
  boolean ambiguous(){return dates.size()>1||(labels>1&&invalid>0);}
  String date(){return !ambiguous()&&dates.size()==1?dates.iterator().next():null;}
 }
 public static String read(String text){return inspect(text).date();}
 public static boolean ambiguous(String text){return inspect(text).ambiguous();}
 private static Result inspect(String text) {
  String normalized = Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toUpperCase(Locale.ROOT);
  Result result=new Result();
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
    result.labels++;
    String tail=line.substring(labels.end());
    Matcher m=NUM.matcher(tail);
    if(!m.find()){m=COMPACT.matcher(tail); if(!m.find()){m=MONTH.matcher(tail);if(!m.find()){result.invalid++;continue;}}}
    try {
     int day=Integer.parseInt(m.group(1)),year=Integer.parseInt(m.group(3)),month;
     String mo=m.group(2);
     if(mo.matches("\\d+")) month=Integer.parseInt(mo);
     else {String[] full={"JANEIRO","FEVEREIRO","MARCO","ABRIL","MAIO","JUNHO","JULHO","AGOSTO","SETEMBRO","OUTUBRO","NOVEMBRO","DEZEMBRO"};int idx=Arrays.asList(MONTHS).indexOf(mo);if(idx<0)idx=Arrays.asList(full).indexOf(mo);month=idx+1;}
     if(year<100)year+=2000;
     result.dates.add(LocalDate.of(year,month,day).toString());
    } catch(RuntimeException ignored){result.invalid++;}
   }
  }
  return result;
 }
}
