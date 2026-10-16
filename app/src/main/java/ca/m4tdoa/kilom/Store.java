package ca.m4tdoa.kilom;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class Store {
    public static final Object LOCK=new Object();
    public static String read(InputStream in)throws IOException{try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=s.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    public static String asset(Context c,String name)throws IOException{return read(c.getAssets().open(name));}
    public static JSONObject state(Context c)throws Exception {
        synchronized(LOCK){AtomicFile f=new AtomicFile(new File(c.getFilesDir(),"state.json"));if(!f.getBaseFile().exists())return new JSONObject("{\"profile\":null,\"trips\":[]}");return new JSONObject(read(f.openRead()));}
    }
    public static void write(Context c,JSONObject state)throws Exception {
        synchronized(LOCK){AtomicFile f=new AtomicFile(new File(c.getFilesDir(),"state.json"));FileOutputStream stream=null;try{stream=f.startWrite();stream.write(state.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(stream);}catch(Exception e){if(stream!=null)f.failWrite(stream);throw e;}}
    }
    public static Engine engine(Context c)throws Exception {
        JSONObject data=new JSONObject(asset(c,"distances.json"));JSONArray a=data.getJSONArray("names"),b=data.getJSONArray("matrix");String[] names=new String[a.length()];double[][] matrix=new double[a.length()][a.length()];
        for(int i=0;i<a.length();i++){names[i]=a.getString(i);for(int j=0;j<a.length();j++)matrix[i][j]=b.getJSONArray(i).getDouble(j);}return new Engine(names,matrix);
    }
    public static Engine.Trip trip(JSONObject o)throws Exception {
        Engine.Trip t=new Engine.Trip();t.id=o.getString("id");t.date=o.getString("date");t.startTime=o.optString("startTime");t.endTime=o.optString("endTime");
        t.carpool=o.optBoolean("carpool");t.passengers=o.optString("passengers");t.extraKm=o.optDouble("extraKm",0);t.reason=o.optString("reason");t.detourLeg=o.optInt("detourLeg",0);
        JSONArray route=o.getJSONArray("route");t.route=new int[route.length()];for(int i=0;i<route.length();i++)t.route[i]=route.getInt(i);return t;
    }
    public static void validate(Context c,JSONObject state)throws Exception {
        JSONObject p=state.getJSONObject("profile");for(String key:new String[]{"name","role","employee","supplier"})if(p.getString(key).trim().isEmpty() || p.getString(key).length()>160)throw new Exception("Compléter tous les champs du profil (160 caractères maximum).");
        LocalDate anchor=LocalDate.parse(p.getString("anchor"));if(anchor.getDayOfWeek()!=DayOfWeek.WEDNESDAY)throw new Exception("Choisir un mercredi de remise.");
        LocalTime.parse(p.optString("exportTime","17:00"));Engine engine=engine(c);JSONArray trips=state.getJSONArray("trips");Set<String> ids=new HashSet<>();
        for(int i=0;i<trips.length();i++){Engine.Trip t=trip(trips.getJSONObject(i));if(!ids.add(t.id))throw new Exception("Identifiant de trajet dupliqué.");engine.rows(t);if(!t.startTime.isEmpty())LocalTime.parse(t.startTime);if(!t.endTime.isEmpty())LocalTime.parse(t.endTime);}
    }
    public static File exportDir(Context c){File f=new File(c.getFilesDir(),"exports");f.mkdirs();return f;}
    public static JSONArray files(Context c)throws Exception{
        JSONArray out=new JSONArray();File[] files=exportDir(c).listFiles((d,n)->n.endsWith(".xlsx"));if(files==null)return out;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files){JSONObject item=new JSONObject();item.put("name",f.getName());item.put("modified",f.lastModified());item.put("bytes",f.length());out.put(item);}return out;
    }
    public static String fingerprint(JSONObject profile,JSONArray trips,String end)throws Exception{
        java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash=md.digest(("grid-displayed-0.0-dome-6.5-v2\n"+profile.toString()+"\n"+trips.toString()+"\n"+end).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.US,"%02x",b));return out.toString();
    }
    public static JSONArray export(Context c,String end,boolean automatic)throws Exception{
        synchronized(LOCK){
            JSONObject state=state(c),p=state.getJSONObject("profile");LocalDate anchor=LocalDate.parse(p.getString("anchor")),finish=LocalDate.parse(end);
            if(!Engine.periodEnd(finish,anchor).equals(finish))throw new Exception("Cette date ne correspond pas au calendrier de remise.");
            LocalDate begin=finish.minusDays(13);JSONArray all=state.getJSONArray("trips"),selected=new JSONArray();List<Engine.Trip> trips=new ArrayList<>();
            for(int i=0;i<all.length();i++){JSONObject o=all.getJSONObject(i);LocalDate d=LocalDate.parse(o.getString("date"));if(!d.isBefore(begin)&&!d.isAfter(finish)){trips.add(trip(o));selected.put(o);}}
            if(trips.isEmpty())return new JSONArray();
            String hash=fingerprint(p,selected,end);String prefKey="export."+end;String old=c.getSharedPreferences("exports",0).getString(prefKey,"");
            if(automatic && old.equals(hash))return new JSONArray();
            Engine.Profile profile=new Engine.Profile();profile.name=p.getString("name");profile.role=p.getString("role");profile.employee=p.getString("employee");profile.supplier=p.getString("supplier");
            Engine engine=engine(c);List<List<Engine.Trip>> pages=engine.pages(trips);
            String first=trips.stream().map(t->t.date).min(String::compareTo).get(),last=trips.stream().map(t->t.date).max(String::compareTo).get();
            String stamp=java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));JSONArray created=new JSONArray();List<File> temporary=new ArrayList<>(),targets=new ArrayList<>();
            try{
                for(int i=0;i<pages.size();i++){
                    String name="KiloM_"+end+"_"+stamp+(pages.size()>1?"_"+(i+1)+"-sur-"+pages.size():"")+".xlsx";File target=new File(exportDir(c),name),temp=new File(exportDir(c),name+".tmp");temporary.add(temp);targets.add(target);
                    engine.export(c.getAssets().open("template.xlsx"),new FileOutputStream(temp),profile,pages.get(i),first,last);
                }
                for(int i=0;i<targets.size();i++){if(!temporary.get(i).renameTo(targets.get(i)))throw new IOException("Impossible d’enregistrer le formulaire.");created.put(targets.get(i).getName());}
            }catch(Exception e){for(File f:temporary)f.delete();for(File f:targets)f.delete();throw e;}
            if(!c.getSharedPreferences("exports",0).edit().putString(prefKey,hash).commit())throw new IOException("Formulaire créé, mais état de remise non enregistré.");
            return created;
        }
    }
    public static int generateDue(Context c)throws Exception{
        synchronized(LOCK){JSONObject s=state(c);if(s.isNull("profile"))return 0;JSONObject p=s.getJSONObject("profile");if(!p.optBoolean("automatic",true))return 0;
            ZoneId zone=ZoneId.of("America/Toronto");LocalDateTime now=LocalDateTime.now(zone);LocalDate anchor=LocalDate.parse(p.getString("anchor"));LocalTime time=LocalTime.parse(p.optString("exportTime","17:00"));TreeSet<LocalDate> due=new TreeSet<>();JSONArray trips=s.getJSONArray("trips");
            for(int i=0;i<trips.length();i++){LocalDate end=Engine.periodEnd(LocalDate.parse(trips.getJSONObject(i).getString("date")),anchor);if(!now.isBefore(end.atTime(time)))due.add(end);}
            int count=0;for(LocalDate end:due)count+=export(c,end.toString(),true).length();return count;
        }
    }
    public static File safeFile(Context c,String name)throws Exception{
        if(!name.matches("KiloM_[A-Za-z0-9_.-]+\\.xlsx"))throw new Exception("Fichier invalide.");File f=new File(exportDir(c),name);if(!f.isFile())throw new FileNotFoundException(name);return f;
    }
}
