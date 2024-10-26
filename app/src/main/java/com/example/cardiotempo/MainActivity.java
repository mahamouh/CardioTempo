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
    private static final String SERVER_IP = "192.168.1.26";
    private static final int SERVER_PORT = 5000;
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

                    // Vérifier si le message contient le motif "Volume envoyé au client:"
                    if (message.contains("Volume envoyé au client:")) {
                        try {
                            // Extraire la valeur numérique après "Volume envoyé au client:"
                            String[] parts = message.split(":");
                            int newVolume = Integer.parseInt(parts[1].trim());

                            // Vérifier que le volume est dans les limites autorisées
                            if (newVolume >= 0 && newVolume <= maxVolume) {
                                runOnUiThread(() -> {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                                    currentVolume = newVolume; // Mise à jour du volume actuel
                                    setPlayerVolume(currentVolume, maxVolume);
                                    System.out.println("Volume mis à jour à : " + currentVolume);
                                });
                            } else {
                                System.out.println("Volume reçu en dehors des limites : " + newVolume);
                            }
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
            currentVolume++;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, customMaxVolume);
            System.out.println("Volume augmenté à : " + currentVolume);
        }
    }

    private void decreaseVolume() {
        if (!isAutoMode && currentVolume > 0) {
            currentVolume--;
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
