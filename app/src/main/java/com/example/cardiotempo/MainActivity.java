package com.example.cardiotempo;

import androidx.appcompat.app.AppCompatActivity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int maxVolume;
    private int currentVolume;
    private Socket socket;
    private static final String SERVER_IP = "100.83.111.17";
    private static final int SERVER_PORT = 5000;
    private boolean isAutoMode = false;

    // Constantes pour le volume initial et maximal personnalisé
    private static final float INITIAL_VOLUME_PERCENTAGE = 0.3f;
    private static final float CUSTOM_MAX_VOLUME_PERCENTAGE = 1f;

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
                new SendAutoModeNotificationTask().execute();
            }
        });

        new InitializeSocketTask().execute();
    }

    private class InitializeSocketTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                System.out.println("Connecté au serveur à " + SERVER_IP + ":" + SERVER_PORT);
                InputStream inputStream = socket.getInputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    String message = new String(buffer, 0, bytesRead).trim();
                    System.out.println("Message brut reçu du serveur : " + message);

                    if (message.contains("Volume envoyé au client:")) {
                        try {
                            String[] parts = message.split(":");
                            int newVolume = Integer.parseInt(parts[1].trim());
                            updateVolumeFromServer(newVolume); // Utilise la méthode de normalisation
                        } catch (NumberFormatException e) {
                            System.out.println("Erreur de format du volume reçu : " + message);
                        }
                    } else {
                        System.out.println("Message non reconnu : " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Erreur lors de la connexion au serveur : " + e.getMessage());
            }
            return null;
        }
    }

    private void updateVolumeFromServer(int receivedVolume) {
        int normalizedVolume = Math.min(receivedVolume, maxVolume); // Limite le volume reçu au max du client
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, normalizedVolume, 0);
        currentVolume = normalizedVolume;
        setPlayerVolume(currentVolume, maxVolume);
        System.out.println("Volume mis à jour depuis le serveur : " + currentVolume);
    }

    private class SendAutoModeNotificationTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            if (socket != null && socket.isConnected()) {
                try {
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write("AUTO_MODE_ON".getBytes());
                    outputStream.flush();
                    System.out.println("Notification de mode automatique envoyée");
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Erreur lors de l'envoi de la notification : " + e.getMessage());
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
            currentVolume += 5;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, customMaxVolume);
            System.out.println("Volume augmenté à : " + currentVolume);
        }
    }

    private void decreaseVolume() {
        if (!isAutoMode && currentVolume > 0) {
            currentVolume -= 5;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, maxVolume);
            System.out.println("Volume diminué à : " + currentVolume);
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
