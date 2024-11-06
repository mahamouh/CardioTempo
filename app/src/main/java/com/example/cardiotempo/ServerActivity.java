package com.example.cardiotempo;

import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerActivity extends AppCompatActivity {

    private static final String TAG = "ServerActivity";
    private AudioManager audioManager;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private static final int SERVER_PORT = 5000;
    private SeekBar volumeControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        volumeControl = findViewById(R.id.volumeSeekBar);
        volumeControl.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volumeControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int clientMaxVolume = 150; // Volume max de l'échelle client
                int normalizedVolume = normalizeVolumeForClient(progress, clientMaxVolume);
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

        new Thread(this::startServer).start();
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            Log.d(TAG, "Serveur démarré, en attente de connexion...");

            clientSocket = serverSocket.accept();
            Log.d(TAG, "Client connecté: " + clientSocket.getInetAddress());

            InputStream inputStream = clientSocket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            String message = new String(buffer, 0, bytesRead);
            Log.d(TAG, "Message reçu du client: " + message);

            if ("AUTO_MODE_ON".equals(message)) {
                runOnUiThread(() -> {
                    volumeControl.setEnabled(true);
                    Log.d(TAG, "Mode automatique activé.");
                });
            }

            inputStream.close();
            clientSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Erreur dans le serveur: ", e);
        }
    }

    private int normalizeVolumeForClient(int serverVolume, int clientMaxVolume) {
        int serverMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return (serverVolume * clientMaxVolume) / serverMaxVolume;
    }

    private void sendVolumeToClient(int volume) {
        new SendVolumeTask().execute(volume);
    }

    private class SendVolumeTask extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... volumes) {
            try {
                if (clientSocket != null && clientSocket.isConnected()) {
                    OutputStream outputStream = clientSocket.getOutputStream();
                    PrintWriter writer = new PrintWriter(outputStream, true);
                    writer.println("Volume envoyé au client: " + volumes[0]);
                    Log.d(TAG, "Volume envoyé au client : " + volumes[0]);
                }
            } catch (IOException e) {
                Log.e(TAG, "Erreur lors de l'envoi du volume au client: ", e);
            }
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Erreur lors de la fermeture des sockets: ", e);
        }
    }
}
