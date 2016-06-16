// based on http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask

package com.trikset.gamepad;

import android.os.AsyncTask;

import com.demo.mjpeg.MjpegInputStream;
import com.demo.mjpeg.MjpegView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;


class StartReadMjpegAsync extends AsyncTask<URI, Void, MjpegInputStream> {
    private final MjpegView mv;

    StartReadMjpegAsync(MjpegView mv) {
        this.mv = mv;
    }

   // @Nullable
    @Override
    protected MjpegInputStream doInBackground(URI... uris) {
        URI uri = uris[0];
        if (uri != null) {

            try {
                URL u = uri.toURL();
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                return new MjpegInputStream(c.getInputStream());

            } catch (IOException e) {
                e.printStackTrace();
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
