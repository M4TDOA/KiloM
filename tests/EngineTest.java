package ca.m4tdoa.kilom;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public final class EngineTest {
    static int assertions=0;
    static void ok(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    static void near(double a,double b,String m){ok(Math.abs(a-b)<0.0000001,m+" "+a+" != "+b);}
    static Engine.Trip trip(String date,int...route){Engine.Trip t=new Engine.Trip();t.date=date;t.route=route;return t;}
    static Map<String,Element> cells(byte[] xml)throws Exception{DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);Document d=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));NodeList list=d.getElementsByTagNameNS("*","c");Map<String,Element> out=new HashMap<>();for(int i=0;i<list.getLength();i++){Element e=(Element)list.item(i);out.put(e.getAttribute("r"),e);}return out;}
    static String tag(Element e,String tag){NodeList nodes=e.getElementsByTagNameNS("*",tag);return nodes.getLength()==0?"":nodes.item(0).getTextContent();}
    public static void main(String[] args)throws Exception{
        List<String> lines=Files.readAllLines(Paths.get(args[0]));String[] names=lines.get(0).split("\t");double[][] matrix=new double[names.length][names.length];for(int i=0;i<names.length;i++){String[] row=lines.get(i+1).split("\t");for(int j=0;j<names.length;j++)matrix[i][j]=Double.parseDouble(row[j]);}
        Engine e=new Engine(names,matrix);int admin=1,beaudoin=6,redempteur=44;
        near(matrix[admin][beaudoin],1.6,"Admin -> Beaudoin");near(matrix[beaudoin][admin],1.7,"Directional distance");
        near(matrix[admin][26],6.5,"Confirmed Admin -> Dome");near(matrix[26][admin],6.5,"Displayed Dome -> Admin");
        Engine.Trip asymmetric=trip("2026-09-28",admin,26,admin);near(e.tripKm(asymmetric),13.0,"Confirmed Dome round trip");
        Engine.Trip simple=trip("2026-09-28",admin,redempteur,admin);simple.startTime="09:00";simple.endTime="09:30";
        ok(e.rows(simple).size()==1,"Round trip compressed");near(e.tripKm(simple),.8,"Simple AR outbound doubled");near(e.rows(simple).get(0).reimbursement,3.15,"One minimum");
        Engine.Trip multi=trip("2026-09-28",admin,beaudoin,redempteur,admin);List<Engine.Row> rows=e.rows(multi);
        near(rows.get(0).legKm,1.6,"Leg 1");near(rows.get(1).legKm,2.0,"Leg 2");near(rows.get(2).legKm,.4,"Leg 3");near(e.tripKm(multi),4.0,"Sum of displayed grid legs");
        ok(!rows.get(0).charged&&!rows.get(1).charged&&rows.get(2).charged,"Charge only last line");near(rows.get(2).reimbursement,3.15,"Minimum not applied per leg");
        Engine.Trip oneWay=trip("2026-10-02",admin,2);near(e.tripKm(oneWay),3.3,"One way");ok(e.rows(oneWay).get(0).type.equals("Aller seulement"),"One way label");
        Engine.Trip detour=trip("2026-10-01",admin,redempteur,admin);detour.extraKm=2.5;detour.reason="Accident & route fermée";detour.detourLeg=1;
        near(e.tripKm(detour),3.3,"Detour once");ok(e.rows(detour).size()==2,"Detour detailed");ok(e.rows(detour).get(0).reason.isEmpty(),"Only affected leg reason");
        Engine.Trip carpool=trip("2026-10-03",admin,2,admin);carpool.carpool=true;carpool.passengers="Jean & Marie";near(e.rows(carpool).get(0).reimbursement,3.3*2*.813,"Carpool rate");
        for(String d:new String[]{"2026-09-17","2026-09-28","2026-09-30"})ok(Engine.periodEnd(LocalDate.parse(d),LocalDate.parse("2026-09-30")).toString().equals("2026-09-30"),"Period "+d);
        ok(Engine.periodEnd(LocalDate.parse("2026-10-01"),LocalDate.parse("2026-09-30")).toString().equals("2026-10-14"),"Next period");
        ok(Engine.periodEnd(LocalDate.parse("2026-09-16"),LocalDate.parse("2026-09-30")).toString().equals("2026-09-16"),"Earlier period");
        ok(Engine.periodEnd(LocalDate.parse("2026-11-01"),LocalDate.parse("2026-09-30")).toString().equals("2026-11-11"),"DST does not affect cycle");
        multi.startTime="10:00";List<Engine.Trip> many=new ArrayList<>();for(int i=0;i<34;i++)many.add(simple);many.add(multi);List<List<Engine.Trip>> pages=e.pages(many);ok(pages.size()==2,"Overflow two files");ok(pages.get(0).size()==34&&pages.get(1).size()==1,"No split within trip");
        Engine.Trip invalid=trip("2026-09-28",admin,admin);boolean rejected=false;try{e.rows(invalid);}catch(IllegalArgumentException ex){rejected=true;}ok(rejected,"Adjacent duplicate rejected");
        Engine.Profile p=new Engine.Profile();p.name="Profil de test";p.role="Opérateur informatique";p.employee="00123";p.supplier="00456";
        String output=args[2];e.export(new FileInputStream(args[1]),new FileOutputStream(output),p,Arrays.asList(simple,multi,detour,oneWay,carpool),"2026-09-28","2026-10-03");
        ZipFile actual=new ZipFile(output),original=new ZipFile(args[1]);
        Enumeration<? extends ZipEntry> entries=original.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();String n=entry.getName();ok(actual.getEntry(n)!=null,"Entry retained "+n);if(!n.equals("xl/worksheets/sheet1.xml")&&!n.equals("xl/workbook.xml"))ok(Arrays.equals(original.getInputStream(entry).readAllBytes(),actual.getInputStream(actual.getEntry(n)).readAllBytes()),"Original bytes retained "+n);}
        Map<String,Element> c=cells(actual.getInputStream(actual.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
        ok(tag(c.get("B7"),"t").equals(p.name),"Profile name");ok(tag(c.get("G7"),"t").equals(p.role),"Profile role");ok(tag(c.get("J7"),"t").equals("00123"),"Leading zero matricule");ok(tag(c.get("M7"),"t").equals("00456"),"Leading zero supplier");
        near(Double.parseDouble(tag(c.get("J4"),"v")),46293,"Excel first date");near(Double.parseDouble(tag(c.get("J5"),"v")),46298,"Excel last date");
        ok(tag(c.get("F12"),"t").equals("Aller/retour"),"Excel simple type");near(Double.parseDouble(tag(c.get("J12"),"v")),.4,"Excel one-way km");
        near(Double.parseDouble(tag(c.get("I13"),"v")),1.6,"Excel per-leg km");ok(tag(c.get("J13"),"v").isEmpty(),"No premature km total");ok(tag(c.get("J15"),"f").equals("SUM(I13:I15)"),"Auditable sum");near(Double.parseDouble(tag(c.get("J15"),"v")),4.0,"Excel circuit total");
        ok(c.get("M12").getChildNodes().getLength()==0,"Blank expense is truly empty for arithmetic");ok(tag(c.get("G17"),"t").contains("Accident & route fermée"),"Escaped detour reason");
        double expected=3.15+3.15+3.15+3.15+3.3*2*.813;near(Double.parseDouble(tag(c.get("N48"),"v")),expected,"Grand total cache");
        for(int i=12;i<=46;i++)ok(tag(c.get("K"+i),"f").contains("MAX(3.15,"),"Formula row "+i);
        DocumentBuilderFactory sf=DocumentBuilderFactory.newInstance();sf.setNamespaceAware(true);
        Document styles=sf.newDocumentBuilder().parse(actual.getInputStream(actual.getEntry("xl/styles.xml")));
        NodeList formats=((Element)styles.getElementsByTagNameNS("*","cellXfs").item(0)).getElementsByTagNameNS("*","xf");
        ok(((Element)formats.item(Integer.parseInt(c.get("I19").getAttribute("s")))).getAttribute("numFmtId").equals("0"),"I19 numeric, never date");
        ok(((Element)formats.item(Integer.parseInt(c.get("A19").getAttribute("s")))).getAttribute("numFmtId").equals("14"),"A19 remains date");
        ok(tag(c.get("E48"),"t").equals(p.name),"Full profile name in signature anchor E48");
        near(Double.parseDouble(tag(c.get("J48"),"v")),46298,"Signature date equals last trip date");
        Map<String,String> codes=new HashMap<>();NodeList nfs=styles.getElementsByTagNameNS("*","numFmt");
        for(int i=0;i<nfs.getLength();i++){Element f=(Element)nfs.item(i);codes.put(f.getAttribute("numFmtId"),f.getAttribute("formatCode"));}
        for(String address:new String[]{"J5","J48","K48"}){
            Element xf=(Element)formats.item(Integer.parseInt(c.get(address).getAttribute("s")));
            ok("yyyy-mm-dd".equals(codes.get(xf.getAttribute("numFmtId"))),"ISO date format "+address);
        }

        List<Engine.Trip> september=Arrays.asList(trip("2026-09-28",1,6,44,1),trip("2026-09-28",1,44,1),trip("2026-09-29",1,40,23,20,1),trip("2026-09-29",1,28,20,1),trip("2026-09-30",1,26,17,28,1));
        double periodAmount=0;for(Engine.Trip t:september){List<Engine.Row> rr=e.rows(t);periodAmount+=rr.get(rr.size()-1).reimbursement;}
        near(periodAmount,63.565,"September supplied routes, grid-based amount");
        near(e.tripKm(september.get(2)),27.6,"September 29 grid kilometres");
        near(e.tripKm(september.get(4)),37.2,"September 30 grid kilometres");
        e.export(new FileInputStream(args[1]),new FileOutputStream(output+".period.xlsx"),p,september,"2026-09-28","2026-09-30");
        try(ZipFile period=new ZipFile(output+".period.xlsx")){
            Map<String,Element> cc=cells(period.getInputStream(period.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
            near(Double.parseDouble(tag(cc.get("I19"),"v")),10.9,"Numeric km at row 19");
            near(Double.parseDouble(tag(cc.get("N48"),"v")),63.565,"Export amount remains grid-based");
        }
        // User-confirmed reference: 6.5 km both ways, 13.0 km and $8.45 per outing.
        near(e.tripKm(asymmetric),13.0,"Dome total");
        near(e.tripKm(trip("2026-10-08",26,admin,26)),13.0,"Dome reverse total");
        near(e.rows(asymmetric).get(0).reimbursement,8.45,"Dome reimbursement");
        e.export(new FileInputStream(args[1]),new FileOutputStream(output+".dome.xlsx"),p,Arrays.asList(asymmetric),"2026-09-28","2026-09-28");
        try(ZipFile dome=new ZipFile(output+".dome.xlsx")){
            Map<String,Element> cc=cells(dome.getInputStream(dome.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
            ok(tag(cc.get("F12"),"t").equals("Aller/retour"),"Dome keeps original dropdown value");
            near(Double.parseDouble(tag(cc.get("J12"),"v")),6.5,"Dome exported single-leg km");
            near(Double.parseDouble(tag(cc.get("K12"),"v")),8.45,"Dome exported amount");
            ok(tag(cc.get("K12"),"f").contains("J12*0.65*2"),"Original Excel doubling formula");
            near(Double.parseDouble(tag(cc.get("N48"),"v")),8.45,"Dome exported grand total");
        }
        original.close();actual.close();System.out.println("PASS: "+assertions+" engine/template assertions; output="+output);
    }
}
