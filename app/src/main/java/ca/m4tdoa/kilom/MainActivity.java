package ca.m4tdoa.kilom;
import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.json.*;

public final class MainActivity extends AppCompatActivity {
    WebView web;FrameLayout root;boolean resetPromptOpen=false;String pendingSave;boolean ready=false;final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle saved){setTheme(isDarkTheme()?R.style.AppThemeDark:R.style.AppTheme);super.onCreate(saved);
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(ready)web.evaluateJavascript("window.handleBack&&handleBack()",null);else finish();}});
        applyTheme();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root=new FrameLayout(this);root.setBackgroundColor(themeColor());
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        web=new WebView(this);web.setBackgroundColor(themeColor());root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager.getInstance().setAcceptCookie(false);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        WebSettings settings=web.getSettings();settings.setSafeBrowsingEnabled(true);settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);settings.setSupportMultipleWindows(false);settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if(Build.VERSION.SDK_INT>=29)web.setForceDarkAllowed(false);
        if(Build.VERSION.SDK_INT>=33)settings.setAlgorithmicDarkeningAllowed(false);
        web.addJavascriptInterface(new Bridge(),"KiloNative");web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result){dialogBuilder().setMessage(message).setPositiveButton("OK",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result){dialogBuilder().setMessage(message).setPositiveButton("Confirmer",(d,w)->result.confirm()).setNegativeButton("Annuler",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri uri=r.getUrl();String path=uri.getPath();if("https".equals(uri.getScheme())&&"kilom.local".equals(uri.getHost())&&uri.getPort()==-1&&uri.getUserInfo()==null&&path!=null&&path.matches("/[A-Za-z0-9_.-]+")){
                    String name=path.substring(1);String mime=name.endsWith(".html")?"text/html":name.endsWith(".js")?"application/javascript":name.endsWith(".css")?"text/css":name.endsWith(".json")?"application/json":name.endsWith(".png")?"image/png":name.endsWith(".ttf")?"font/ttf":"text/plain";
                    try{return new WebResourceResponse(mime,"UTF-8",getAssets().open(name));}catch(Exception ignored){}
                }return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView v,String url){ready=true;refresh();}
        });web.loadUrl("https://kilom.local/index.html");
        if(saved!=null)pendingSave=saved.getString("pendingSave");
    }
    String themeMode(){String value=getSharedPreferences("appearance",0).getString("theme","light");return validTheme(value)?value:"light";}
    boolean validTheme(String value){return "light".equals(value)||"dark".equals(value)||"pink".equals(value)||"oled".equals(value);}
    boolean isDarkTheme(){String mode=themeMode();return "dark".equals(mode)||"oled".equals(mode);}
    int themeColor(){String mode=themeMode();return Color.parseColor("oled".equals(mode)?"#000000":"dark".equals(mode)?"#111318":"pink".equals(mode)?"#fff8fa":"#f9f9ff");}
    void applyTheme(){int color=themeColor();getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color));
        int flags=getWindow().getDecorView().getSystemUiVisibility(),lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(isDarkTheme()?flags&~lightFlags:flags|lightFlags);if(root!=null)root.setBackgroundColor(color);if(web!=null)web.setBackgroundColor(color);
    }
    AlertDialog.Builder dialogBuilder(){return new MaterialAlertDialogBuilder(new ContextThemeWrapper(this,isDarkTheme()?R.style.AppThemeDark:R.style.AppTheme));}
    static void clearTemporaryFiles(File directory)throws IOException{File[] children=directory.listFiles();if(children==null)return;for(File file:children){if(file.isDirectory())clearTemporaryFiles(file);if(!file.delete()&&file.exists())throw new IOException("Certains fichiers temporaires sont occupés.");}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);refresh();}
    @Override protected void onResume(){super.onResume();if(ready)refresh();}
    void refresh(){worker.execute(()->{try{Reminder.notifyReady(this,Store.generateDue(this));Reminder.schedule(this);String tab=getIntent().getStringExtra("tab");if(tab!=null){runOnUiThread(()->web.evaluateJavascript("window.openTab&&openTab("+JSONObject.quote(tab)+")",null));getIntent().removeExtra("tab");}runOnUiThread(()->web.evaluateJavascript("window.refreshExports&&refreshExports()",null));}catch(Exception e){message("Export automatique : "+e.getMessage());}});}
    void message(String msg){runOnUiThread(()->{if(ready)web.evaluateJavascript("window.toast&&toast("+JSONObject.quote(msg)+")",null);else Toast.makeText(this,msg,Toast.LENGTH_LONG).show();});}
    @Override public void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("pendingSave",pendingSave);}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();if(web!=null){web.removeJavascriptInterface("KiloNative");web.destroy();}}
    void share(String name,boolean view){try{
        Store.safeFile(this,name);Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".exports").appendPath(name).build();
        Intent intent=new Intent(view?Intent.ACTION_VIEW:Intent.ACTION_SEND);
        if(view)intent.setDataAndType(uri,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");else{intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");intent.putExtra(Intent.EXTRA_STREAM,uri);intent.putExtra(Intent.EXTRA_SUBJECT,"Réclamation de frais de déplacement");}
        intent.setClipData(ClipData.newRawUri(name,uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent,view?"Ouvrir le formulaire":"Partager le formulaire"));
    }catch(Exception e){message("Aucune application disponible. Utilisez Enregistrer pour garder le fichier.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingSave!=null){String name=pendingSave;Uri uri=data.getData();worker.execute(()->{try(InputStream in=new FileInputStream(Store.safeFile(this,name));OutputStream out=getContentResolver().openOutputStream(uri)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);message("Fichier Excel enregistré.");}catch(Exception e){message("Enregistrement impossible : "+e.getMessage());}});}pendingSave=null;}
    final class Bridge {
        @JavascriptInterface public String getTheme(){return themeMode();}
        @JavascriptInterface public String setTheme(String mode){if(!validTheme(mode))return "Thème invalide.";if(!getSharedPreferences("appearance",0).edit().putString("theme",mode).commit())return "Impossible d’enregistrer le thème.";runOnUiThread(()->applyTheme());return "";}
        @JavascriptInterface public void clearCache(){runOnUiThread(()->{
            try{web.clearCache(true);}catch(Exception e){web.evaluateJavascript("window.cacheCleared&&cacheCleared('Nettoyage impossible.')",null);return;}
            worker.execute(()->{String error=null;try{clearTemporaryFiles(getCacheDir());}catch(Exception e){error="Certains fichiers temporaires sont occupés. Réessayez après avoir rouvert KiloM.";}final String result=error;runOnUiThread(()->{if(!isFinishing())web.evaluateJavascript("window.cacheCleared&&cacheCleared("+(result==null?"null":JSONObject.quote(result))+")",null);});});
        });}
        @JavascriptInterface public void requestDataReset(){runOnUiThread(()->{if(resetPromptOpen)return;resetPromptOpen=true;
            AlertDialog dialog=dialogBuilder().setTitle("Effacer toutes les données ?").setMessage("Le profil, les sorties, la progression, les options et les fichiers Excel conservés dans KiloM seront supprimés définitivement. Les copies enregistrées ailleurs restent disponibles. KiloM se fermera et repartira à zéro à la prochaine ouverture.")
                .setNegativeButton("Annuler",(d,w)->{}).setPositiveButton("Tout effacer",(d,w)->{try{if(!((ActivityManager)getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData())message("Effacement impossible. Réessayez depuis les paramètres Android.");}catch(Exception e){message("Effacement impossible. Réessayez depuis les paramètres Android.");}}).create();
            dialog.setOnDismissListener(d->resetPromptOpen=false);dialog.show();
        });}
        @JavascriptInterface public String load(){try{return Store.state(MainActivity.this).toString();}catch(Exception e){return "{\"error\":"+JSONObject.quote("Les données n’ont pas pu être lues. Ne désinstallez pas l’app. "+e.getMessage())+"}";}}
        @JavascriptInterface public String save(String json){try{JSONObject data=new JSONObject(json);Store.validate(MainActivity.this,data);Store.write(MainActivity.this,data);Reminder.schedule(MainActivity.this);worker.execute(()->{try{Reminder.notifyReady(MainActivity.this,Store.generateDue(MainActivity.this));runOnUiThread(()->web.evaluateJavascript("window.refreshExports&&refreshExports()",null));}catch(Exception e){message(e.getMessage());}});return "";}catch(Exception e){return "Enregistrement impossible : "+e.getMessage();}}
        @JavascriptInterface public String files(){try{return Store.files(MainActivity.this).toString();}catch(Exception e){return "{\"error\":\"Impossible de lire les fichiers Excel.\"}";}}
        @JavascriptInterface public void exportPeriod(String end){worker.execute(()->{try{JSONArray files=Store.export(MainActivity.this,end,false);runOnUiThread(()->web.evaluateJavascript("exportFinished("+files.toString()+",null)",null));}catch(Exception e){runOnUiThread(()->web.evaluateJavascript("exportFinished([],"+JSONObject.quote(e.getMessage())+")",null));}});}
        @JavascriptInterface public void shareFile(String name){runOnUiThread(()->share(name,false));}
        @JavascriptInterface public void openMaps(String value){runOnUiThread(()->{try{
            Uri uri=Uri.parse(value);
            boolean google="www.google.com".equals(uri.getHost())&&"/maps/dir/".equals(uri.getPath())&&"1".equals(uri.getQueryParameter("api"));
            boolean waze="waze.com".equals(uri.getHost())&&"/ul".equals(uri.getPath())&&"yes".equals(uri.getQueryParameter("navigate"))&&uri.getQueryParameter("q")!=null;
            if(value.length()>2048||!"https".equals(uri.getScheme())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getFragment()!=null||!(google||waze))throw new IllegalArgumentException();
            Intent intent=new Intent(Intent.ACTION_VIEW,uri).setPackage(waze?"com.waze":"com.google.android.apps.maps");
            try{startActivity(intent);}catch(ActivityNotFoundException absent){startActivity(new Intent(Intent.ACTION_VIEW,uri));}
        }catch(Exception e){message("Impossible d’ouvrir la navigation. Vérifiez que l’application choisie ou un navigateur est installé.");}});}
        @JavascriptInterface public void saveFile(String name){runOnUiThread(()->{try{Store.safeFile(MainActivity.this,name);pendingSave=name;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,42);}catch(Exception e){message(e.getMessage());}});}
        @JavascriptInterface public void notifications(){runOnUiThread(()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);else message("Les alertes sont déjà autorisées ou gérées dans les réglages Android.");});}
        @JavascriptInterface public boolean notificationsEnabled(){return ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();}
        @JavascriptInterface public void close(){runOnUiThread(()->finish());}
    }
}
