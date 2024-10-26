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

    private static final String TAG = "ServerActivity"; // Pour identifier les logs
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
                sendVolumeToClient(progress); // Envoyer le volume au client
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                Log.d(TAG, "Volume changé: " + progress); // Log du changement de volume
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

            clientSocket = serverSocket.accept(); // Accepte la connexion du client
            Log.d(TAG, "Client connecté: " + clientSocket.getInetAddress());

            InputStream inputStream = clientSocket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {  // Écouter en continu
                String message = new String(buffer, 0, bytesRead).trim();
                Log.d(TAG, "Message reçu du client: " + message);

                if ("AUTO_MODE_ON".equals(message)) {
                    runOnUiThread(() -> {
                        volumeControl.setEnabled(true);
                        Log.d(TAG, "Mode automatique activé.");
                    });
                }
            }
            // Ne pas fermer inputStream et clientSocket ici pour permettre la communication continue
        } catch (IOException e) {
            Log.e(TAG, "Erreur dans le serveur: ", e);
        }
    }


    private void sendVolumeToClient(int volume) {
        new SendVolumeTask().execute(volume); // Exécute la tâche asynchrone pour envoyer le volume
    }

    private class SendVolumeTask extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... volumes) {
            try {
                if (clientSocket != null && clientSocket.isConnected()) {
                    OutputStream outputStream = clientSocket.getOutputStream();
                    PrintWriter writer = new PrintWriter(outputStream);
                    // Ajouter le préfixe attendu par le client
                    writer.println("Volume envoyé au client:" + volumes[0]);
                    writer.flush();
                    Log.d(TAG, "Volume envoyé au client: " + volumes[0]); // Log du volume envoyé
                } else {
                    Log.w(TAG, "Le client n'est pas connecté ou le socket est nul.");
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
            if (serverSocket != null) {
                serverSocket.close();
                Log.d(TAG, "Serveur arrêté.");
            }
            if (clientSocket != null) {
                clientSocket.close();
                Log.d(TAG, "Client déconnecté.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Erreur lors de la fermeture des sockets: ", e);
        }
    }
}
