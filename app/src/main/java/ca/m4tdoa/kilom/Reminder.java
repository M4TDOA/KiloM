package ca.m4tdoa.kilom;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import java.time.*;

public class Reminder extends BroadcastReceiver {
    public static void schedule(Context c){try{
        Intent intent=new Intent(c,Reminder.class).setAction(c.getPackageName()+".PREPARE_EXPORT");PendingIntent pi=PendingIntent.getBroadcast(c,80,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);alarm.cancel(pi);
        PendingIntent legacy=PendingIntent.getBroadcast(c,80,new Intent(c,Reminder.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(legacy!=null){alarm.cancel(legacy);legacy.cancel();}
        JSONObject state=Store.state(c);if(state.isNull("profile"))return;JSONObject p=state.getJSONObject("profile");if(!p.optBoolean("automatic",true))return;
        ZoneId zone=ZoneId.of("America/Toronto");ZonedDateTime now=ZonedDateTime.now(zone);LocalDate end=Engine.periodEnd(now.toLocalDate(),LocalDate.parse(p.getString("anchor")));
        ZonedDateTime next=end.atTime(LocalTime.parse(p.optString("exportTime","17:00"))).atZone(zone);if(!next.isAfter(now))next=next.plusDays(14);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),pi);
    }catch(Exception e){android.util.Log.e("KiloM","Programmation impossible",e);}}
    public static void notifyReady(Context c,int n){
        if(n<=0)return;NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("exports","Formulaires Excel prêts",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(c,MainActivity.class).putExtra("tab","exports");PendingIntent pi=PendingIntent.getActivity(c,81,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,"exports").setSmallIcon(R.drawable.ic_notification).setContentTitle("Votre Excel KiloM est prêt").setContentText(n+" formulaire"+(n>1?"s":"")+" à vérifier et à partager.").setContentIntent(pi).setAutoCancel(true).build();
        try{nm.notify(81,notification);}catch(SecurityException ignored){}
    }
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null)return;
        String action=intent.getAction();
        if(!(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||(c.getPackageName()+".PREPARE_EXPORT").equals(action)))return;
        PendingResult result=goAsync();new Thread(()->{try{notifyReady(c,Store.generateDue(c));}catch(Exception e){android.util.Log.e("KiloM","Export automatique impossible",e);}finally{schedule(c);result.finish();}}).start();}
}
