package ca.m4tdoa.kilom;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Pure Java calculation and lossless template filling. No network or external dependencies. */
public final class Engine {
    public static class Profile { public String name="", role="", employee="", supplier=""; }
    public static class Trip {
        public String id="", date="", startTime="", endTime="", reason="", passengers="";
        public int[] route;
        public boolean carpool;
        public int detourLeg=0;
        public double extraKm;
    }
    public static class Row {
        public String date, startTime="", endTime="", from, to, type, reason="", passengers="";
        public boolean carpool, showLeg, charged;
        public double legKm, claimKm, reimbursement;
        public String sumFormula;
    }
    public final String[] names;
    public final double[][] distances;
    public Engine(String[] names, double[][] distances) { this.names=names; this.distances=distances; }
    public static double km(double n) { return Math.round(n*1000d)/1000d; }
    public static String number(double n) { return java.math.BigDecimal.valueOf(km(n)).stripTrailingZeros().toPlainString(); }
    public static LocalDate periodEnd(LocalDate date, LocalDate anchor) {
        if(anchor.getDayOfWeek()!=DayOfWeek.WEDNESDAY) throw new IllegalArgumentException("La remise doit être un mercredi.");
        long delta=ChronoUnit.DAYS.between(anchor,date);
        return anchor.plusDays(-Math.floorDiv(-delta,14)*14);
    }
    public List<Row> rows(Trip t) {
        LocalDate.parse(t.date);
        if(t.route==null || t.route.length<2 || t.route.length>36) throw new IllegalArgumentException("Choisir de 1 à 35 étapes.");
        if(!Double.isFinite(t.extraKm) || t.extraKm<0 || t.extraKm>10000) throw new IllegalArgumentException("Kilométrage de détour invalide.");
        if(t.extraKm>0 && (t.reason.trim().isEmpty() || t.detourLeg<0 || t.detourLeg>=t.route.length-1)) throw new IllegalArgumentException("Indiquer la raison et l’étape du détour.");
        if(t.carpool && t.passengers.trim().isEmpty()) throw new IllegalArgumentException("Indiquer les personnes accompagnées.");
        for(int i=0;i<t.route.length;i++) {
            if(t.route[i]<0 || t.route[i]>=names.length) throw new IllegalArgumentException("Établissement inconnu.");
            if(i>0 && t.route[i]==t.route[i-1]) throw new IllegalArgumentException("Deux étapes consécutives sont identiques.");
        }
        boolean compact=t.route.length==3 && t.route[0]==t.route[2] && t.extraKm==0;
        int count=compact?1:t.route.length-1;
        List<Row> out=new ArrayList<>(); double total=0;
        for(int i=0;i<count;i++) {
            Row r=new Row(); r.date=t.date; r.from=names[t.route[i]]; r.to=names[t.route[i+1]];
            r.type=compact?"Aller/retour":"Aller seulement"; r.carpool=t.carpool; r.passengers=t.passengers;
            r.legKm=km(distances[t.route[i]][t.route[i+1]]+(i==t.detourLeg?t.extraKm:0));
            r.showLeg=count>1; total=km(total+r.legKm);
            if(i==0) r.startTime=t.startTime;
            if(i==count-1) r.endTime=t.endTime;
            if(t.extraKm>0 && i==t.detourLeg) r.reason="Détour : "+t.reason+" (+"+number(t.extraKm)+" km)";
            out.add(r);
        }
        Row last=out.get(out.size()-1); last.charged=true; last.claimKm=total;
        double amount=total*(compact?2:1)*(t.carpool?0.813:0.65);
        last.reimbursement=total>0?Math.max(3.15,amount):0;
        return out;
    }
    public double tripKm(Trip t) { List<Row> r=rows(t); Row last=r.get(r.size()-1); return km(last.claimKm*(last.type.equals("Aller/retour")?2:1)); }
    public List<List<Trip>> pages(List<Trip> trips) {
        List<Trip> sorted=new ArrayList<>(trips);
        Collections.sort(sorted, (a,b)-> {int c=a.date.compareTo(b.date);return c==0?a.startTime.compareTo(b.startTime):c;});
        List<List<Trip>> result=new ArrayList<>(); List<Trip> page=new ArrayList<>(); int used=0;
        for(Trip t:sorted) {int size=rows(t).size(); if(used+size>35){result.add(page);page=new ArrayList<>();used=0;}page.add(t);used+=size;}
        if(!page.isEmpty()) result.add(page); return result;
    }
    static class Cell {
        String value, formula; boolean text;
        Cell(String v,boolean t,String f){value=v;text=t;formula=f;}
    }
    static void text(Map<String,Cell> m,String address,String value){m.put(address,new Cell(value,true,null));}
    static void num(Map<String,Cell> m,String address,double value){m.put(address,new Cell(Double.toString(value),false,null));}
    static void formula(Map<String,Cell> m,String address,String formula,double value){m.put(address,new Cell(Double.toString(value),false,formula));}
    public static long serial(String date){return ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),LocalDate.parse(date));}
    static double time(String t){LocalTime tm=LocalTime.parse(t);return (tm.getHour()*60+tm.getMinute())/1440d;}
    static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    static String fill(String source,Map<String,Cell> values){
        Pattern p=Pattern.compile("<c\\b([^>]*?\\br=\"([^\"]+)\"[^>]*?)(?:\\s*/>|>(.*?)</c>)",Pattern.DOTALL);
        Matcher matcher=p.matcher(source);StringBuffer output=new StringBuffer();Set<String> found=new HashSet<>();
        while(matcher.find()){
            Cell c=values.get(matcher.group(2)); if(c==null)continue; found.add(matcher.group(2));
            String attrs=matcher.group(1).replaceAll("\\s+t=\"[^\"]*\"","").replaceAll("/\\s*$","");
            String body;
            if(c.text && c.formula!=null)body="<c"+attrs+" t=\"str\"><f>"+xml(c.formula)+"</f><v>"+xml(c.value)+"</v></c>";
            else if(c.text && c.value.isEmpty())body="<c"+attrs+"/>";
            else if(c.text) body="<c"+attrs+" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+xml(c.value)+"</t></is></c>";
            else body="<c"+attrs+">"+(c.formula==null?"":"<f>"+xml(c.formula)+"</f>")+"<v>"+xml(c.value)+"</v></c>";
            matcher.appendReplacement(output,Matcher.quoteReplacement(body));
        }
        matcher.appendTail(output);
        if(!found.containsAll(values.keySet())){Set<String> missing=new HashSet<>(values.keySet());missing.removeAll(found);throw new IllegalStateException("Cellules absentes : "+missing);}
        return output.toString();
    }
    public void export(InputStream template,OutputStream target,Profile profile,List<Trip> page,String firstDate,String lastDate)throws IOException {
        Map<String,Cell> values=new HashMap<>();
        text(values,"B7",profile.name);text(values,"G7",profile.role);text(values,"J7",profile.employee);text(values,"M7",profile.supplier);
        num(values,"J4",serial(firstDate));num(values,"J5",serial(lastDate));
        text(values,"E48",profile.name);num(values,"J48",serial(lastDate));
        // Format AAMMJJ conservé exactement comme dans le modèle CSSPO fourni.
        values.put("M3",new Cell(LocalDate.parse(lastDate).format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd")),true,"IF(J5<>\"\",TEXT(J5,\"AAMMJJ\"),\"\")"));
        int cursor=12; double grand=0;
        for(int n=12;n<=46;n++) {
            for(String col:new String[]{"A","B","C","D","E","F","G","H","I","J","L","M"})text(values,col+n,"");
            formula(values,"K"+n,reimbursementFormula(n),0);formula(values,"N"+n,"+K"+n+"+M"+n,0);
        }
        for(Trip trip:page) {
            List<Row> rows=rows(trip);int start=cursor;List<String> literals=new ArrayList<>();
            for(Row row:rows){
                int n=cursor++; if(n>46)throw new IllegalArgumentException("Le formulaire dépasse 35 lignes.");
                num(values,"A"+n,serial(row.date));text(values,"D"+n,row.from);text(values,"E"+n,row.to);text(values,"F"+n,row.type);text(values,"G"+n,row.reason);
                text(values,"H"+n,row.carpool?"Oui":"Non");
                if(!row.startTime.isEmpty())num(values,"B"+n,time(row.startTime));if(!row.endTime.isEmpty())num(values,"C"+n,time(row.endTime));
                if(row.showLeg){
                    if(row.carpool)text(values,"I"+n,number(row.legKm).replace('.',',')+" km — "+row.passengers);
                    else num(values,"I"+n,row.legKm);
                }else if(row.carpool)text(values,"I"+n,row.passengers);
                literals.add(number(row.legKm));
                if(row.charged) {
                    if(rows.size()>1)formula(values,"J"+n,row.carpool?"SUM("+String.join(",",literals)+")":"SUM(I"+start+":I"+n+")",row.claimKm);
                    else num(values,"J"+n,row.claimKm);
                    formula(values,"K"+n,reimbursementFormula(n),row.reimbursement);formula(values,"N"+n,"+K"+n+"+M"+n,row.reimbursement);grand+=row.reimbursement;
                }
            }
        }
        formula(values,"N48","SUM(N12:N46)",grand);
        try(ZipInputStream zin=new ZipInputStream(template);ZipOutputStream zout=new ZipOutputStream(target)){
            ZipEntry entry;byte[] buffer=new byte[16384];
            while((entry=zin.getNextEntry())!=null){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();int n;while((n=zin.read(buffer))!=-1)bytes.write(buffer,0,n);
                byte[] data=bytes.toByteArray();String name=entry.getName();
                if(name.equals("xl/worksheets/sheet1.xml"))data=fill(new String(data,StandardCharsets.UTF_8),values).getBytes(StandardCharsets.UTF_8);
                if(name.equals("xl/workbook.xml"))data=new String(data,StandardCharsets.UTF_8).replaceAll("<calcPr[^>]*/>","<calcPr calcId=\"191028\" fullCalcOnLoad=\"1\" forceFullCalc=\"1\"/>").getBytes(StandardCharsets.UTF_8);
                zout.putNextEntry(new ZipEntry(name));zout.write(data);zout.closeEntry();
            }
        }
    }
    static String reimbursementFormula(int n){String j="J"+n,f="F"+n,h="H"+n;return "IF("+j+"<>0,IF("+j+"<>\"\",IF("+f+"=\"Aller/retour\",IF("+h+"=\"Oui\",MAX(3.15,"+j+"*0.813*2),MAX(3.15,"+j+"*0.65*2)),IF("+h+"=\"Oui\",MAX(3.15,"+j+"*0.813),MAX(3.15,"+j+"*0.65))),0),0)";}
}
