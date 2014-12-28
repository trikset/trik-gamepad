// based on http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask

package com.trik.gamepad;

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.AsyncTask;
import android.util.Log;

import com.demo.mjpeg.MjpegInputStream;
import com.demo.mjpeg.MjpegView;

public class StartReadMjpegAsync extends AsyncTask<URI, Void, Void> {
    private final MjpegView mv;

    StartReadMjpegAsync(MjpegView mv) {
        this.mv = mv;
    }

    @Override
    protected Void doInBackground(URI... uris) {
        HttpResponse res = null;
        URI uri = uris[0];
        if (uri != null) {
            try {
                DefaultHttpClient httpclient = new DefaultHttpClient();
                res = httpclient.execute(new HttpGet(uri));
                MjpegInputStream stream = new MjpegInputStream(res.getEntity().getContent());
                mv.setSource(stream);
                return null;
            } catch (ClientProtocolException e) {
                Log.e(this.getClass().getSimpleName(), Log.getStackTraceString(e));

            } catch (IOException e) {
                Log.e(this.getClass().getSimpleName(), Log.getStackTraceString(e));
            }
        }
        mv.setSource(null);
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        mv.startPlayback();
    }
};
