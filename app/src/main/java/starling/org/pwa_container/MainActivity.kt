package starling.org.pwa_container

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.design.widget.BottomNavigationView
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import starling.org.pwa_container.JavaScriptInterface.Companion.urls
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL


internal class JavaScriptInterface {

    var instance: MainActivity? = null;

    val lastKnownLocation: String
        @android.webkit.JavascriptInterface
        get() = "latlong: "+  this.instance!!.lastKnownLocation

    constructor(instance: MainActivity) {
        this.instance = instance;
    }


    @android.webkit.JavascriptInterface
    fun sayHi(name: String) : String {
        Log.e("hi ", name)
        return "hi " + name

    }

    @android.webkit.JavascriptInterface
    fun execute(command: String, param: String?) {
        Log.e("command ", command)
        Log.e("param ", param)
        var url = urls[command]
        if (param != null) {
            url = url + "?" + param
        }

        //this.instance!!.nav2Url(url)
    }

    companion object {

        var urls: MutableMap<String, String> = HashMap()
        var versionsURLs: MutableMap<String, String> = HashMap()
        var versions: MutableMap<String, String> = HashMap()

        var updated: MutableSet<String> = HashSet()
    }
}

internal class WebContent(var contentType: String, var content: ByteArray)

class MainActivity : AppCompatActivity() {


    private var mywebview: WebView? = null
    private var webSettings: WebSettings? = null
    private var frameLayout: FrameLayout? = null
    private var menuItem: MenuItem? = null;
    private val resources = HashMap<String, WebContent>()
    private val visitedUrls = HashSet<String>()
    private var mLocationManager: LocationManager? = null


    companion object {
        private val LOCATION_REQUEST_CODE = 101
        val LOADING_HTML = "<html><body><h1>loading ....</h1></body>" +
                "<script>" +
                "window.setTimeout(()=>window.location.reload(),5000);" +
                "</script>" +
                "</html>";
        val DEMO_HTML = "<html><body><h1>DEMO ....</h1></body>" +
                "<a href=\"javascript:alert(webview.getLastKnownLocation())\">location</a><br>" +
                "<a href=\"javascript:alert(webview.sayHi('eddy'))\">say hi</a>" +
                "</html>";
    }


    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->

        var result = false;
        when (item.itemId) {
            R.id.navigation_home -> {
                result = true
            }
            R.id.navigation_dashboard -> {
                result = true
            }
            R.id.navigation_notifications -> {
                result = true
            }
        }
        urlbox.setText(item.title)
        menuItem = item;
        Log.d("loadUrl", "" + urlbox.text);
        mywebview!!.loadUrl("" + urlbox.text);
        Toast.makeText(this@MainActivity, urlbox.getText(), Toast.LENGTH_SHORT).show();
        return@OnNavigationItemSelectedListener result;
    }

    private val myKeyListener = View.OnKeyListener { v, keyCode, event ->

        //If the event is a key-down event on the "enter" button
        if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                (keyCode == KeyEvent.KEYCODE_ENTER)) {
            mywebview!!.loadUrl("" + urlbox.text);
            //var item = findViewById<>(navigation.selectedItemId)
            if (menuItem!=null){
                menuItem!!.setTitle("" + urlbox.text);
            }

            // Perform action on key press
            Toast.makeText(this@MainActivity, urlbox.getText(), Toast.LENGTH_SHORT).show();
            true;
        }

        false;

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(R.layout.activity_main)
        frameLayout = findViewById(R.id.frame) as FrameLayout
        mywebview = findViewById(R.id.webview) as WebView;
        //navigation.selectedItemId
//    urlbox.setOnClickListener(View.OnClickListener {
//        Log.d("", "" + urlbox.text);
//        mywebview!!.loadUrl("" + urlbox.text);
//        //var item = findViewById<>(navigation.selectedItemId)
//        menuItem!!.setTitle("" + urlbox.text);
//        //navigation.()navigation.selectedItemId
//    });
        urlbox.setOnKeyListener(myKeyListener);
        urlbox.clearFocus()
        webSettings = mywebview!!.settings
        webSettings!!.javaScriptEnabled = true
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        webSettings!!.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings!!.setAppCacheEnabled(false)

        resources.put("https://www.demo.nl", WebContent("text/html", DEMO_HTML.toByteArray()))
        urlbox.setText("https://www.demo.nl")

        Log.d("VERSION:", softwareVersion + " " + appLabel)

        mLocationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager



        mywebview!!.setWebChromeClient(object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return super.onJsAlert(view, url, message, result)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.e("jslog", consoleMessage.message())
                return super.onConsoleMessage(consoleMessage)
            }

        })



        mywebview!!.setWebViewClient(object : WebViewClient() {

            //protected UrlCache urlCache = null;
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                Log.e("shouldOverrideUrlLoading", request.toString())
                //Log.d("VERSION:", softwareVersion)
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                //mywebview.clearCache(true);
                val requestURL = request.url.toString()
                val offLinePage = DEMO_HTML;//LOADING_HTML;
                var webResResp: WebResourceResponse? = null;
                Log.d("request", requestURL)

                var webContent = getWebContent(requestURL);
                Log.d("retrieve: ", requestURL)
                if (webContent != null) {
                    Log.d("retrieved from cache: ", requestURL)
                    val data = ByteArrayInputStream(webContent.content)
                    webResResp = WebResourceResponse(webContent.contentType, "UTF-8", data)
                } else {
                    Log.d("connecting to : ", request.url.toString())
                    webContent = getUrlContent(requestURL)
                    if (webContent != null) {
                        Log.d("downloaded: ", requestURL)
                        val data = ByteArrayInputStream(webContent.content)
                        webResResp = WebResourceResponse(webContent.contentType, "UTF-8", data)
                    }

                    if (webResResp == null) {
                        var mimetype = request.requestHeaders["Accept"];
                        mimetype = mimetype!!.split(",")[0];
                        Log.e("offline page for location", request.url.toString())
                        val data = ByteArrayInputStream(offLinePage.toByteArray())
                        webResResp = WebResourceResponse(mimetype, "UTF-8", data)
                    }
                }

                Log.d("returning response for: ", request.url.toString())
                return webResResp;
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.d("onPageFinished", url)

                //val downloadUpdate = true
//                if (downloadUpdate) {
//                val snackbar = Snackbar
//                        .make(frameLayout!!, "Website has been updated", Snackbar.LENGTH_LONG)
//
//                snackbar.show()
                //}
            }
        })
        webview.loadUrl("https://www.demo.nl")
        mywebview!!.addJavascriptInterface(JavaScriptInterface(this), "webview")
    }

    internal fun getWebContent(requestURL: String): WebContent? {

        //final String requestURL = url.toString();

        val wc = resources[requestURL]

        if (wc != null || visitedUrls.contains(requestURL)) {
            Log.d("cached: ", requestURL)
            return wc
        }
        Log.d("request", requestURL)
        visitedUrls.add(requestURL);

        Thread(Runnable {
            Log.d("thread for: ", requestURL)
            getUrlContent(requestURL)
        }).start()
        return resources[requestURL]
    }

    private fun getUrlContent(requestURL: String): WebContent? {
        var contentType: String = ""
        var urlConnection: HttpURLConnection? = null
        var webContent: WebContent? = null;
        try {
            var url = URL(requestURL)
            urlConnection = url.openConnection() as HttpURLConnection
            val bin = BufferedInputStream(urlConnection.inputStream)
            contentType = "" + urlConnection.contentType
            Log.d("Content-type: ", contentType)
            contentType = contentType.split(";".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[0]
            val buffer = ByteArrayOutputStream()
            var nRead: Int
            val data = ByteArray(1024 * 8);
            nRead = bin.read(data, 0, data.size);
            while (nRead != -1) {
                buffer.write(data, 0, nRead)
                Log.d("reading....", requestURL)
                nRead = bin.read(data, 0, data.size);
            }
            buffer.flush()
            val byteArray = buffer.toByteArray()
            webContent = WebContent(contentType, byteArray);
            resources.put(requestURL, webContent)
            Log.d("store: ", requestURL)
            Log.d("NEW", requestURL)
            //MainActivity.instance.mywebview.clearCache(true);


        } catch (e: Exception) {
            Log.e("error reading:", requestURL, e)
            Log.e("error:", e.message)
            visitedUrls.remove(requestURL);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect()
            }
        }
        return webContent;
    }

    val lastKnownLocation: String
        get() {


            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_REQUEST_CODE)
            }
            val location = this.mLocationManager!!.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (location != null) {
                Log.d("LASTKNOWNLOCATION", location.latitude.toString() + " " + location.longitude)
                return location.latitude.toString() + "," + location.longitude
            }

            return ""
        }

    internal val connected: Boolean
        get() {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val activeNetwork = cm.activeNetworkInfo
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting

        }

    val appLabel: String
        get() {
            val packageManager = packageManager
            var applicationInfo: ApplicationInfo? = null
            try {
                applicationInfo = packageManager.getApplicationInfo(getApplicationInfo().packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
            }

            return (if (applicationInfo != null) packageManager.getApplicationLabel(applicationInfo) else "Unknown") as String
        }

    private val softwareVersion: String
        get() {
            val pi: PackageInfo
            try {
                pi = packageManager.getPackageInfo(packageName, 0)
                return "version:" + pi.versionName + " package: " + pi.packageName
            } catch (e: PackageManager.NameNotFoundException) {
                return "na"
            }

        }


}
