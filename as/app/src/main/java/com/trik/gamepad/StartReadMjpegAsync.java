// based on http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask

package com.trik.gamepad;

import android.os.AsyncTask;
import android.util.Log;

import com.demo.mjpeg.MjpegInputStream;
import com.demo.mjpeg.MjpegView;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.net.URI;

class StartReadMjpegAsync extends AsyncTask<URI, Void, MjpegInputStream> {
    private final MjpegView mv;

    StartReadMjpegAsync(MjpegView mv) {
        this.mv = mv;
    }

    @Override
    protected MjpegInputStream doInBackground(URI... uris) {
        HttpResponse res;
        URI uri = uris[0];
        if (uri != null) {
            try {
                DefaultHttpClient httpclient = new DefaultHttpClient();
                res = httpclient.execute(new HttpGet(uri));
                return new MjpegInputStream(res.getEntity().getContent());
            } catch (IOException e) {
                Log.e(this.getClass().getSimpleName(), Log.getStackTraceString(e));

            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(MjpegInputStream result) {
        mv.setSource(result);
        mv.startPlayback();
    }
}
