import br.primor.ocrlab.Expiry;
import java.nio.charset.StandardCharsets;
public class ExpiryCli {
 public static void main(String[] args)throws Exception{
  String text=new String(System.in.readAllBytes(),StandardCharsets.UTF_8);
  String date=Expiry.read(text);
  System.out.println(Expiry.ambiguous(text)?"BLOCK":date==null?"":date);
 }
}
