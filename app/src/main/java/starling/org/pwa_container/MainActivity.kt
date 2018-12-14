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
import java.net.*
import java.util.*
import java.util.zip.ZipInputStream


internal class WebContent(var contentType: String, var content: ByteArray, var lastModified:String) : Serializable

class MainActivity : AppCompatActivity() {


    private var mywebview1: WebView? = null
    private var mywebview2: WebView? = null

    private var webSettings: WebSettings? = null
    private var frameLayout: FrameLayout? = null
    private var menuItem: MenuItem? = null;
    private val resources = HashMap<String, WebContent>()
    private val visitedUrls = HashSet<String>()
    private var mLocationManager: LocationManager? = null
    private val httpd = AndroidWebServer( 8080);



    companion object {
        private val LOCATION_REQUEST_CODE = 101

        val LOADING_HTML = "<html><body><h1>loading ....</h1>\n" +
                "<h3>address: {{hostname}}</h3>\n"  +
                "<script>" +
                "  window.setTimeout(()=>window.location.reload(),5000);\n" +
                "</script>"+
                "</body>" +
                "</html>";
        val DEMO_HTML = "<html><body><h1>APP 1</h1></body>" +
                "<a href=\"javascript:alert(webview.getLastKnownLocation())\">location</a><br>" +
                "<a href=\"javascript:alert(webview.sayHi('eddy'))\">say hi</a><br>" +
                "<a href=\"10.0.2.2:8080/\">test nano httpd</a><br>" +
                "href: <script>document.write(window.location.href);</script><br>" +
                "host: <script>document.write(window.location.hostname);</script><br>" +
                "address: {{hostname}}"  +
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
        if (urlbox.text.startsWith("http") || urlbox.text.startsWith("file://")) {
            mywebview1!!.loadUrl("" + urlbox.text);
        } else {
            var wc = getWebContent("" + urlbox.text);
            if (wc != null) {
                mywebview1!!.loadDataWithBaseURL(null, String(wc.content), "text/html", "UTF-8", null);
            } else {
                Log.d("connecting to : ", ""+urlbox.text)
                mywebview1!!.loadDataWithBaseURL(null, LOADING_HTML, "text/html", "UTF-8", null);
            }
        }


        //Util.scheduleJob(baseContext);
        Toast.makeText(this@MainActivity, urlbox.getText(), Toast.LENGTH_SHORT).show();
        return@OnNavigationItemSelectedListener result;
    }

    private val myKeyListener = View.OnKeyListener { v, keyCode, event ->

        //If the event is a key-down event on the "enter" button
        Log.d("v: ", ""+ v)
        if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                (keyCode == KeyEvent.KEYCODE_ENTER)) {
            mywebview1!!.loadUrl("" + urlbox.text);
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
        mywebview1 = findViewById(R.id.webview1) as WebView;
        //mywebview2 = findViewById(R.id.webview2) as WebView;
        //mywebview2!!.visibility= 0;
        urlbox.setOnKeyListener(myKeyListener);
        urlbox.clearFocus()
        webSettings = mywebview1!!.settings
        webSettings!!.javaScriptEnabled = true
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        webSettings!!.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings!!.setAppCacheEnabled(false)


        resources.put("https://www.demo.nl", WebContent("text/html", DEMO_HTML.toByteArray(),"" + Date()))
        urlbox.setText("https://www.demo.nl")


        //loadCachedResources()

        fillCacheDefaults()

        Log.d("VERSION:", softwareVersion + " " + appLabel)

        mLocationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager



        mywebview1!!.setWebChromeClient(object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                return super.onJsAlert(view, url, message, result)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.e("jslog", consoleMessage.message())
                return super.onConsoleMessage(consoleMessage)
            }

        })



        mywebview1!!.setWebViewClient(object : WebViewClient() {

            //protected UrlCache urlCache = null;
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                Log.e("shouldOverrideUrlLoading", request.toString())
                //Log.d("VERSION:", softwareVersion)
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                //mywebview.clearCache(true);
                val requestURL = request.url.toString()
                Log.d("retrieve: ", requestURL)
                val offLinePage = LOADING_HTML;
                var webResResp: WebResourceResponse? = null;
                var bytes = ByteArray(0)
                Log.d("request", requestURL)
                var data = ByteArrayInputStream(bytes)
                var mimetype = "text/html";
                var webContent = getWebContent(requestURL);
                var loadedUrl  = request.url.toString()
                val ZIP_MIMETYPE = "application/zip"
                val localhostName = "" + Arrays.toString(java.net.InetAddress.getLocalHost().address)


                if (webContent != null) {
                    Log.d("retrieved from cache: ", requestURL)
                    data = ByteArrayInputStream(webContent.content)
                    mimetype = webContent.contentType;

                    //releative to zip file urls hava contentType of zip file in cache
                    if (resources[mimetype] != null) {
                        val pair = getZipResource(data, mimetype, requestURL)
                        data = pair.first
                        mimetype = pair.second

                    }
                } else {
                    Log.d("connecting to : ", request.url.toString())
                    val acceptHeader = request.requestHeaders["Accept"];
                    mimetype = acceptHeader!!.split(",")[0];
                    Log.e("offline page for location", request.url.toString())
                    val offLinePage = offLinePage.replace("{{hostname}}",localhostName)
                    data = ByteArrayInputStream(offLinePage.toByteArray())

                }




                webResResp = WebResourceResponse(mimetype, "UTF-8", data)
                Log.d("returning response for: ", loadedUrl)
                return webResResp;
            }

            override fun onPageFinished(view: WebView, url: String) {
                finishPageLoad(url)

            }
        })
        webview1.loadUrl("https://www.demo.nl")
        mywebview1!!.addJavascriptInterface(JavaScriptInterface(this), "webview")

        httpd.start();
    }//oncreate

    private fun finishPageLoad(url: String) {
        Log.d("onPageFinished", url)

        var oos: ObjectOutputStream? = null
        try {
            val fOut = openFileOutput("resources.bin", Context.MODE_PRIVATE)
            oos = ObjectOutputStream(fOut)
            oos.writeObject(resources)
            Log.d("cache", "Cache stored!")
        } catch (e: Exception) {
            Log.e("error saving resources : ", "", e)
        } finally {
            if (oos !== null) {
                oos.close()
            }
            Log.d("cache urls: \n", Arrays.toString(resources.keys.toTypedArray()).replace(", ", "\n"))
            var res = "bin or js";
            var key = url.replace(Regex("/$"), "")
            if (resources[key] != null && resources[key]!!.contentType == "text/html") {
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

    private fun getZipResource(data: ByteArrayInputStream, mimetype: String, requestURL: String): Pair<ByteArrayInputStream, String> {
        var data1 = data
        var mimetype1 = mimetype
        data1 = ByteArrayInputStream(resources[mimetype1]!!.content)
        var zis = ZipInputStream(data1);
        val buffer = ByteArray(2048)
        var output = ByteArrayOutputStream();
        // now copy out of the zip archive until all bytes are copied
        var entry = zis.nextEntry;

        while (entry != null) {
            val name = entry.getName()
            Log.d(" file:  ", name);
            if (requestURL.endsWith("/" + entry)) {
                var len = zis.read(buffer)
                while (len > 0) {
                    output.write(buffer, 0, len)
                    len = zis.read(buffer)
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry;
        }
        zis.close()
        data1 = ByteArrayInputStream(output.toByteArray())
        mimetype1 = extractMimeTypeFromUrl(requestURL)
        return Pair(data1, mimetype1)
    }

    private fun loadZippedSite(wc: WebContent?,requestURL: String ): WebContent {


        var mimetype = wc!!.contentType
        var data  = ByteArrayInputStream(wc!!.content)
        var zis = ZipInputStream(data);
        var wcMain: WebContent = wc;
        // we now iterate through all files in the archive
        var entry = zis.nextEntry;
        var mainPage = ""
        while (entry != null) {
            val name = entry.getName()
            Log.d(" file:  ", entry.getName());
            //find the first html file
            if (name.endsWith(".html") && mainPage.equals("")) {
                mainPage = entry.name
                val buffer = ByteArray(2048)
                var output = ByteArrayOutputStream();
                var len = zis.read(buffer)
                while (len > 0) {
                    output.write(buffer, 0, len)
                    len = zis.read(buffer)
                }

                wcMain = WebContent("text/html", output.toByteArray(), "" + Date())
                Log.d("wcMain",String(wcMain.content));
            }
            try{
                if (!entry.isDirectory){
                  var path = requestURL
                  if(!path.endsWith("/")){
                   path+="/"
                  }
                  path += entry.name;
                  resources[path] = WebContent(requestURL, "".toByteArray(), "" + Date())
                }
                zis.closeEntry()
                entry = zis.nextEntry;
            } catch(e:Exception) {
                Log.d("error zis.closeEntry() ", requestURL)
                break
            }


        }
        zis.close()
        Log.d("wcMain",String(wcMain.content));
        return wcMain;
        //return Triple(data1, loadedUrl1, mimetype1)
    }

    private fun extractMimeTypeFromUrl(requestURL: String): String {
        var result = ""
        if (requestURL.endsWith(".html")) {
            result = "text/html"
        }

        if (requestURL.endsWith(".js")) {
            result = "text/javascript"
        }
        if (requestURL.endsWith("png")) {
            result = "image/png"
        }
        if (requestURL.endsWith("gif")) {
            result = "image/png"
        }
        if (requestURL.endsWith("json")) {
            result = "application/json"
        }
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        httpd.stop();
    }

    private fun fillCacheDefaults() {
        //add a resource html files
        val appIds = intArrayOf(R.raw.app1, R.raw.app2)
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
                resources.put(appUrl, WebContent("text/html", stringBuilder.toString().toByteArray(),"" + Date()))
                cacheDirty = true;
                //resources.put("https://www.app.nl", WebContent("text/html", stringBuilder.toString().toByteArray()))
                //mywebview!!.loadDataWithBaseURL(null, stringBuilder.toString(), "text/html", "UTF-8", null);
                count++;
            } catch (e: IOException) {
                e.printStackTrace();
            }
            try {
                var inputStream = getResources().openRawResource(R.raw.game);
                val byteArray = readStreamBytes(inputStream, "");
                resources.put("file://game.zip/", WebContent("application/zip", byteArray,"" + Date()))

            }catch (e: IOException) {
                e.printStackTrace();
            }
        }
    }

    private fun loadCachedResources() {
        var ois: ObjectInputStream? = null
        try {
            val fIn = openFileInput("resources.bin")
            ois = ObjectInputStream(fIn)
            val res = ois.readObject()
            resources.putAll(res as Map<out String, WebContent>)
            cacheDirty = true;
            Log.d("cache", "Cache loaded....!")

        } catch (e: Exception) {
            Log.e("error loading resources : ", "", e)
        } finally {
            if (ois !== null) {
                ois.close()
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
        if (wc != null) {
            Log.d("cached: ", requestURL)
            wc = loadZippedSite(wc, requestURL)

        }
        Log.d("request", requestURL)
        if (requestURL.indexOf("?") < 0) {
            visitedUrls.add(requestURL);
        }
        //skip request for default localy stored apps
        if (requestURL.startsWith("http")) {
            Thread(Runnable {
                Log.d("thread for: ", requestURL)
                getUrlContent(requestURL, wc)
            }).start()
        }
        return wc
    }


    private fun getUrlContent(requestURL: String, wc:WebContent?): WebContent? {
        //var policy: ThreadPolicy = ThreadPolicy.Builder().permitAll().build();
        //setThreadPolicy(policy);

        var contentType = ""
        var urlConnection: HttpURLConnection? = null
        var webContent: WebContent? = null;

        if (requestURL.endsWith("/favicon.ico")) {
            return null;
        }
        try {
            var url = URL(requestURL)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.connectTimeout = 1
            contentType = "" + urlConnection.contentType
            Log.d("Content-type: ", contentType)
            contentType = contentType.split(";".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[0]

            var lastModified = "";
            var headers = urlConnection.headerFields;

            if (wc !=null) {
                Log.d("Cache hit, sending head request for: ", requestURL)
                urlConnection.setRequestMethod("HEAD");
                urlConnection.getInputStream().close();
                headers = urlConnection.headerFields;

                //headers.entries.forEach { e-> Log.d(""+e.key, ""+ e.value) }
                lastModified = "" + headers.getValue("last-Modified");
                Log.d("head last-Modified: "+ lastModified, "cache lastModified: " + wc?.lastModified)
                if (lastModified.equals(wc?.lastModified)) {
                    Log.d("retreived head lastModified not changed, returning cached value for: ", requestURL)
                    return wc;

                }

            }
            //var headBytes = readStreamBytes(urlConnection.inputStream, requestURL)
            //var headers = headBytes.toString(Charset.defaultCharset())
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.setRequestMethod("GET");


            val byteArray = readStreamBytes(urlConnection.inputStream, requestURL)

            headers = urlConnection.headerFields;
            lastModified = "" + headers.getValue("last-Modified");
            webContent = WebContent(contentType, byteArray, ""+ lastModified);

            //only cache urls without questionmarks
            //so probably parameterized data will not be cached
            if (requestURL.indexOf("?") < 0) {
                resources.put(requestURL, webContent)
                cacheDirty = true;
                Log.d("store: ", requestURL)
                Log.d("NEW", requestURL)
            }

            if (wc!=null){
                Log.d("retreived payload lastModified changed, returning new webContent value for: ", requestURL)
            }


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

    private fun readStreamBytes(inputStream: InputStream,  requestURL: String):ByteArray {

        val bin = BufferedInputStream(inputStream)


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
        return byteArray
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
