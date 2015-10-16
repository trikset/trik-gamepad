// based on http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask

package com.trik.gamepad

import android.os.AsyncTask
import com.demo.mjpeg.MjpegInputStream
import com.demo.mjpeg.MjpegView
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI


internal class StartReadMjpegAsync(private val mv: MjpegView) : AsyncTask<URI, Void, MjpegInputStream>() {

    override fun doInBackground(vararg uris: URI): MjpegInputStream? {
        val uri = uris[0]
            try {
                val u = uri.toURL()
                val c = u.openConnection() as HttpURLConnection
                c.connectTimeout = 5000
                c.readTimeout = 5000
                return MjpegInputStream(c.inputStream)

            } catch (e: IOException) {
                e.printStackTrace()
            }
        return null
    }

    override fun onPostExecute(result: MjpegInputStream) {
        mv.setSource(result)
        mv.startPlayback()
    }
}
