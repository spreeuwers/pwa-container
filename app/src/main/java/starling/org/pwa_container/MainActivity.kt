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
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import starling.org.pwa_container.JavaScriptInterface.Companion.cacheDirty
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


internal class WebContent(var contentType: String, var content: ByteArray) : Serializable

class MainActivity : AppCompatActivity() {


    private var mywebview: WebView? = null
    private var webSettings: WebSettings? = null
    private var frameLayout: FrameLayout? = null
    private var menuItem: MenuItem? = null;
    private val resources = HashMap<String, WebContent>()
    private val visitedUrls = HashSet<String>()
    private var mLocationManager: LocationManager? = null
    private val httpd = AndroidWebServer("localhost", 8080);


    companion object {
        private val LOCATION_REQUEST_CODE = 101
        val LOADING_HTML = "<html><body><h1>loading ....</h1></body>" +
                "<script>" +
                "window.setTimeout(()=>window.location.reload(),5000);" +
                "</script>" +
                "</html>";
        val DEMO_HTML = "<html><body><h1>APP 1</h1></body>" +
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
        if (urlbox.text.startsWith("http")) {
            mywebview!!.loadUrl("" + urlbox.text);
        } else {
            var wc = getWebContent("" + urlbox.text);
            if (wc != null) {
                mywebview!!.loadDataWithBaseURL(null, String(wc!!.content), "text/html", "UTF-8", null);
            }
        }


        //Util.scheduleJob(baseContext);
        Toast.makeText(this@MainActivity, urlbox.getText(), Toast.LENGTH_SHORT).show();
        return@OnNavigationItemSelectedListener result;
    }

    private val myKeyListener = View.OnKeyListener { v, keyCode, event ->

        //If the event is a key-down event on the "enter" button
        if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                (keyCode == KeyEvent.KEYCODE_ENTER)) {
            mywebview!!.loadUrl("" + urlbox.text);
            //var item = findViewById<>(navigation.selectedItemId)
            if (menuItem != null) {
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

        urlbox.setOnKeyListener(myKeyListener);
        urlbox.clearFocus()
        webSettings = mywebview!!.settings
        webSettings!!.javaScriptEnabled = true
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        webSettings!!.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings!!.setAppCacheEnabled(false)

        resources.put("https://www.demo.nl", WebContent("text/html", DEMO_HTML.toByteArray()))
        urlbox.setText("https://www.demo.nl")


        loadCachedResources()

        fillCacheDefaults()

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
                var bytes = ByteArray(0)
                Log.d("request", requestURL)
                var data = ByteArrayInputStream(bytes)
                var mimetype = "text/html";
                var webContent = getWebContent(requestURL);
                var loadedUrl  = request.url.toString()
                val ZIP_MIMETYPE = "application/zip"
                if (loadedUrl.endsWith(".zip")){
                    mimetype = ZIP_MIMETYPE;
                }
                Log.d("retrieve: ", requestURL)
                if (webContent != null) {
                    Log.d("retrieved from cache: ", requestURL)
                    data = ByteArrayInputStream(webContent.content)
                    mimetype = webContent.contentType;

                } else {
                    Log.d("connecting to : ", request.url.toString())
                    webContent = getUrlContent(requestURL)
                    if (webContent != null) {
                        Log.d("downloaded: ", requestURL)
                        data = ByteArrayInputStream(webContent.content)
                        mimetype = webContent.contentType
                        //webResResp = WebResourceResponse(webContent.contentType, "UTF-8", data)
                    }
                    //fallback
                    if (webResResp == null) {
                        val acceptHeader = request.requestHeaders["Accept"];
                        mimetype = acceptHeader!!.split(",")[0];
                        Log.e("offline page for location", request.url.toString())
                        data = ByteArrayInputStream(offLinePage.toByteArray())

                    }
                }

                if (mimetype.equals(ZIP_MIMETYPE)) {
                    var zis = ZipInputStream(data);

                    // we now iterate through all files in the archive testing them
                    // again the predicate filter that we passed in. Only items that
                    // match the filter are expanded.
                    var entry = zis.nextEntry;
                    while (entry  != null) {
                        val name = entry.getName()
                        Log.d(" file:  ", entry.getName());
                        if (name.endsWith(".html")){
                            loadedUrl = name;
                            val buffer = ByteArray(2048)

                            // now copy out of the zip archive until all bytes are copied

                            var output = ByteArrayOutputStream();
                            var len = zis.read(buffer)
                            while (len > 0) {
                                output.write(buffer, 0, len)
                                len = zis.read(buffer)
                            }
                            data = ByteArrayInputStream(output.toByteArray())
                            mimetype = "text/html"
                        }
                        entry = zis.nextEntry;
                    }
                }
                webResResp = WebResourceResponse(mimetype, "UTF-8", data)
                Log.d("returning response for: ", loadedUrl)
                return webResResp;
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.d("onPageFinished", url)

                var oos: ObjectOutputStream? = null
                try {
                    val fOut = openFileOutput("resources.bin", Context.MODE_PRIVATE)
                    var oos = ObjectOutputStream(fOut)
                    oos.writeObject(resources)
                    Log.d("cache", "Cache stored!")
                } catch (e: Exception) {
                    Log.e("error saving resources : ", "", e)
                } finally {
                    if (oos !== null) {
                        oos!!.close()
                    }
                    Log.d("cache urls: \n", Arrays.toString(resources.keys.toTypedArray()).replace(", ", "\n"))
                    var res = "bin or js";
                    var key = url.replace(Regex("/$"), "")
                    if (resources[key] != null && resources[key]!!.contentType == "text/html" ) {
                        res = String(resources[key]!!.content)

                    }

                    Log.d("loaded resource: ", url + ": " + res)
                }
                val downloadUpdate = cacheDirty;
                cacheDirty = false;
                if (downloadUpdate) {
                    val snackbar = Snackbar
                            .make(frameLayout!!, "Website has been updated", Snackbar.LENGTH_LONG)

                    snackbar.show()
                }

            }
        })
        webview.loadUrl("https://www.demo.nl")
        mywebview!!.addJavascriptInterface(JavaScriptInterface(this), "webview")

        httpd.start();
    }//oncreate

    override fun onDestroy() {
        super.onDestroy()
        httpd.stop();
    }

    private fun fillCacheDefaults() {
        //add a resource html files
        val appIds = intArrayOf(R.raw.app1, R.raw.app2, R.raw.app3)
        var count = 1;
        for (appId in appIds) {
            try {
                var inputStream = getResources().openRawResource(appId);
                var bufferedReader = BufferedReader(InputStreamReader(inputStream));
                var stringBuilder = StringBuilder();
                var line = bufferedReader.readLine();
                while (line != null) {
                    stringBuilder.append(line).append("\n");
                    line = bufferedReader.readLine();

                }
                var appUrl = "local.app" + count + ".nl"
                Log.d("fill cache; ", appUrl)
                resources.put(appUrl, WebContent("text/html", stringBuilder.toString().toByteArray()))
                cacheDirty = true;
                //resources.put("https://www.app.nl", WebContent("text/html", stringBuilder.toString().toByteArray()))
                //mywebview!!.loadDataWithBaseURL(null, stringBuilder.toString(), "text/html", "UTF-8", null);
                count++;
            } catch (e: IOException) {
                e.printStackTrace();
            }

        }
    }

    private fun loadCachedResources() {
        var ois: ObjectOutputStream? = null
        try {
            val fIn = openFileInput("resources.bin")
            var ois = ObjectInputStream(fIn)
            val res = ois.readObject()
            resources.putAll(res as Map<out String, WebContent>)
            cacheDirty = true;
            Log.d("cache", "Cache loaded....!")

        } catch (e: Exception) {
            Log.e("error loading resources : ", "", e)
        } finally {
            if (ois !== null) {
                ois!!.close()
            }
        }
    }

    internal fun getWebContent(requestURL: String): WebContent? {

        //final String requestURL = url.toString();
        var key = requestURL.replace(Regex("/$"), "")
        var wc = resources[requestURL]
        if (wc == null) {
            wc = resources[key]
        }
        if (wc != null || visitedUrls.contains(requestURL)) {
            Log.d("cached: ", requestURL)
            return wc
        }
        Log.d("request", requestURL)
        if (requestURL.indexOf("?") < 0) {
            visitedUrls.add(requestURL);
        }

        if (requestURL.startsWith("http")) {
            Thread(Runnable {
                Log.d("thread for: ", requestURL)
                getUrlContent(requestURL)
            }).start()
        }
        return wc
    }

    private fun getUrlContent(requestURL: String): WebContent? {
        var contentType: String = ""
        var urlConnection: HttpURLConnection? = null
        var webContent: WebContent? = null;

        if (requestURL.endsWith("/favicon.ico")) {
            return null;
        }
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

            //only cache urls without questionmarks
            //so probably parameterized data will not be cached
            if (requestURL.indexOf("?") < 0) {
                resources.put(requestURL, webContent)
                cacheDirty = true;
                Log.d("store: ", requestURL)
                Log.d("NEW", requestURL)
            }

            //MainActivity.instance.mywebview.clearCache(true);


        } catch (e: Exception) {
            Log.e("error reading:", requestURL + " " + e.message)
            //Log.e("error:",
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

            return "null"
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
