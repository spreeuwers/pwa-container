package starling.org.pwa_container

import android.util.Log
import android.webkit.JavascriptInterface
import java.util.HashMap
import java.util.HashSet

internal class JavaScriptInterface {

    var instance: MainActivity? = null;

    val lastKnownLocation: String
        @JavascriptInterface
        get() = "latlong: " + this.instance!!.lastKnownLocation

    constructor(instance: MainActivity) {
        this.instance = instance;
    }


    @JavascriptInterface
    fun sayHi(name: String): String {
        Log.e("hi ", name)
        return "hi " + name

    }

    @JavascriptInterface
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
        var cacheDirty = true;
        var updated: MutableSet<String> = HashSet()
    }
}