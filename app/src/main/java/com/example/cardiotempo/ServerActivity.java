package com.example.cardiotempo;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerActivity extends AppCompatActivity {

    private static final String TAG = "ServerActivity";
    private AudioManager audioManager;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private static final int SERVER_PORT = 5000;
    private SeekBar volumeControl;
    private int clientMaxVolume = 90; // 60 % de l'échelle précédente
    private ExecutorService executorService; // Executor pour gérer les threads en arrière-plan
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        volumeControl = findViewById(R.id.volumeSeekBar);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int serverMaxVolume = (int) (0.6 * maxVolume); // 60 % du volume maximal

        volumeControl.setMax(serverMaxVolume);
        int initialVolume = (int) (0.3 * serverMaxVolume); // 30 % du volume maximal fixé à 60 %
        volumeControl.setProgress(initialVolume);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initialVolume, 0);

        volumeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int normalizedVolume = (int) ((progress / (float) volumeControl.getMax()) * clientMaxVolume);
                sendVolumeToClient(normalizedVolume);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                Log.d(TAG, "Volume changé: " + progress + " - Volume normalisé pour le client : " + normalizedVolume);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "Démarrage du suivi du curseur.");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "Arrêt du suivi du curseur.");
            }
        });

        // Initialiser le Handler et l'ExecutorService
        handler = new Handler();
        executorService = Executors.newFixedThreadPool(2); // On crée un pool de threads pour exécuter les tâches en arrière-plan

        new Thread(this::startServer).start(); // Démarrer le serveur dans un thread séparé
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            Log.d(TAG, "Serveur démarré, en attente de connexion...");

            clientSocket = serverSocket.accept();
            Log.d(TAG, "Client connecté: " + clientSocket.getInetAddress());

            // Thread pour écouter les messages entrants du client (réception des données)
            executorService.execute(() -> {
                try {
                    InputStream inputStream = clientSocket.getInputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        String message = new String(buffer, 0, bytesRead).trim();
                        Log.d(TAG, "Message reçu: " + message);

                        if (message.contains("Volume actuel")) {
                            String[] parts = message.split(":");
                            int volume = Integer.parseInt(parts[1].trim());
                            int normalized_volume = (int) (double) (volume / 10);
                            runOnUiThread(() -> volumeControl.setProgress(normalized_volume));  // Mettre à jour la SeekBar dans l'UI
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Erreur de lecture du client : " + e.getMessage());
                }
            });

            // Thread pour envoyer des messages au client toutes les 200ms sans bloquer
            executorService.execute(() -> {
                try {
                    OutputStream outputStream = clientSocket.getOutputStream();
                    PrintWriter writer = new PrintWriter(outputStream, true);
                    while (true) {
                        writer.println("Volume envoyé au client: " + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                        Log.d(TAG, "Volume envoyé au client : " + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
                        Thread.sleep(2000); // Attendre avant d'envoyer un autre message
                    }
                } catch (IOException | InterruptedException e) {
                    Log.e(TAG, "Erreur d'envoi au client : " + e.getMessage());
                }
            });

        } catch (IOException e) {
            Log.e(TAG, "Erreur de serveur : " + e.getMessage());
        }
    }

    private void sendVolumeToClient(int volume) {
        executorService.execute(() -> {
            try {
                if (clientSocket != null && clientSocket.isConnected()) {
                    OutputStream outputStream = clientSocket.getOutputStream();
                    PrintWriter writer = new PrintWriter(outputStream, true);
                    writer.println("Volume envoyé au client: " + volume);
                    Log.d(TAG, "Volume envoyé au client : " + volume);
                }
            } catch (IOException e) {
                Log.e(TAG, "Erreur d'envoi du volume au client : " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Libérer les ressources et arrêter l'executorService
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
