package com.example.cardiotempo;

import androidx.appcompat.app.AppCompatActivity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;

import android.os.AsyncTask;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int maxVolume;
    private int currentVolume;
    private Socket socket;
    private static final String SERVER_IP = "192.168.1.26"; // IP du serveur (à ajuster)
    private static final int SERVER_PORT = 5000; // Port du serveur (à ajuster)
    private boolean isAutoMode = false;

    // Constantes pour le volume initial et maximal personnalisé
    private static final float INITIAL_VOLUME_PERCENTAGE = 0.3f;
    private static final float CUSTOM_MAX_VOLUME_PERCENTAGE = 0.8f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mediaPlayer = MediaPlayer.create(this, R.raw.jetplane);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int customMaxVolume = (int) (maxVolume * CUSTOM_MAX_VOLUME_PERCENTAGE);
        currentVolume = (int) (customMaxVolume * INITIAL_VOLUME_PERCENTAGE);
        setPlayerVolume(currentVolume, customMaxVolume);
        mediaPlayer.start();

        Button increaseVolumeButton = findViewById(R.id.increaseVolumeButton);
        increaseVolumeButton.setOnClickListener(v -> increaseVolume(customMaxVolume));

        Button decreaseVolumeButton = findViewById(R.id.decreaseVolumeButton);
        decreaseVolumeButton.setOnClickListener(v -> decreaseVolume());

        Button autoModeButton = findViewById(R.id.autoModeButton);
        autoModeButton.setOnClickListener(v -> {
            isAutoMode = !isAutoMode;
            if (isAutoMode) {
                // Notifier le serveur que le mode automatique est activé
                new SendAutoModeNotificationTask().execute();
            }
        });

        // Tâche asynchrone pour initialiser la connexion au serveur
        new InitializeSocketTask().execute();
    }

    private class InitializeSocketTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private class SendAutoModeNotificationTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            if (socket != null && socket.isConnected()) {
                try {
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write("AUTO_MODE_ON".getBytes());
                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    private void setPlayerVolume(int systemVolume, int customMaxVolume) {
        float volume = (float) systemVolume / customMaxVolume;
        mediaPlayer.setVolume(volume, volume);
    }

    private void increaseVolume(int customMaxVolume) {
        if (!isAutoMode && currentVolume < customMaxVolume) {
            currentVolume++;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, customMaxVolume);
        }
    }

    private void decreaseVolume() {
        if (!isAutoMode && currentVolume > 0) {
            currentVolume--;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, maxVolume);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
