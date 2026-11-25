package br.primor.ocrlab;
import org.junit.Test;
import static org.junit.Assert.*;
public class ExpiryTest {
 @Test public void eightTranscriptions() {
  String[] texts={"FAB: 01/07/26\nVAL: 30/06/27\nLOT: 07/26 OP5","VAL 240427\nLOT 2409261","FAB21/07/26\nVAL21/01/27","L06 M35 150526 A\nVENCE: 15/05/27","FAB: 29 SET 26\nVAL: 28 SET 27","FAB: 20/08/2026\nVAL: 20/08/2027","FAB: 15 DEZ 25\nVAL: 08 JUN 27","L:VS19726622P3 16:46\nVAL:12/05/27"};
  String[] expected={"2027-06-30","2027-04-24","2027-01-21","2027-05-15","2027-09-28","2027-08-20","2027-06-08","2027-05-12"};
  for(int i=0;i<texts.length;i++)assertEquals("sample "+(i+1),expected[i],Expiry.read(texts[i]));
 }
 @Test public void rejectsFabricationAndLot(){assertNull(Expiry.read("FAB: 01/07/26\nLOT: 240427"));}
 @Test public void rejectsImpossible(){assertNull(Expiry.read("VAL: 31/02/27"));assertNull(Expiry.read("VAL 321327"));}
 @Test public void rejectsConflict(){assertNull(Expiry.read("VAL: 15/05/27\nVAL: 12/05/27"));}
 @Test public void expiredStillRecognized(){assertEquals("2024-02-29",Expiry.read("VAL 29/02/24"));}
 @Test public void refusesGuesses(){assertNull(Expiry.read("VAL 2O/08/2027"));assertNull(Expiry.read("VAL: JUN/27"));assertNull(Expiry.read("15/05/27"));}
 @Test public void accentedAndFullMonth(){assertEquals("2027-09-28",Expiry.read("Validade: 28 setembro 2027"));}
 @Test public void splitLabelRegression(){
  assertEquals("2027-06-30",Expiry.read("FAB: 01/07/26\nVAL:\n30/06/27\nLOT: 07/26 OP5"));
  assertEquals("2027-05-15",Expiry.read("L06 M35 150526 A\nVENCE:\n15/05/27"));
  assertEquals("2027-06-08",Expiry.read("VAL:\n08 JUN 27"));
  assertNull(Expiry.read("VAL:\nLOT: 240427"));
  assertNull(Expiry.read("VAL:\n240427"));
  assertNull(Expiry.read("VAL:\nFAB: 15/05/27"));
  assertNull(Expiry.read("VAL:\n15/05/27\nVAL: 12/05/27"));
 }
 @Test public void strictTokens(){
  assertNull(Expiry.read("LOTE 1VAL 240427"));
  assertNull(Expiry.read("VAL 240427A"));
  assertNull(Expiry.read("VAL 08 JUNXYZ 27"));
  assertEquals("2027-06-08",Expiry.read("VAL 08 JUNHO 27"));
 }
 @Test public void ambiguityCannotBeRescuedByIgnoringMalformedLabel(){
  assertNull(Expiry.read("VAL: 15/05/27\nVAL: 1205/27"));
  assertTrue(Expiry.ambiguous("VAL: 15/05/27\nVAL: 1205/27"));
  assertTrue(Expiry.ambiguous("VAL: 15/05/27\nVAL: 12/05/27"));
  assertFalse(Expiry.ambiguous("VAL: 15/05/27\nVAL: 15/05/27"));
  assertEquals("2027-05-15",Expiry.read("VAL: 15/05/27\nVAL: 15/05/27"));
 }
}
