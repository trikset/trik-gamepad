// based on http://stackoverflow.com/questions/10550139/android-ics-and-mjpeg-using-asynctask

package com.trikset.gamepad;

import android.os.AsyncTask;
import androidx.annotation.Nullable;
import android.util.Log;

import com.demo.mjpeg.MjpegInputStream;
import com.demo.mjpeg.MjpegView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;


class StartReadMjpegAsync extends AsyncTask<URL, Void, MjpegInputStream> {
    private final MjpegView mv;

    StartReadMjpegAsync(MjpegView mv) {
        this.mv = mv;
    }

   // @Nullable
    @Nullable
    @Override
    protected MjpegInputStream doInBackground(URL... urls) {
        URL url = urls[0];
        if (url != null) {
            try {
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                MjpegInputStream s = new MjpegInputStream(c.getInputStream());
                Log.i("JPGReader", "Restarted connection.");
                return s;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(MjpegInputStream result) {
        mv.stopPlayback();
        mv.setSource(result);
        mv.startPlayback();
    }

}
