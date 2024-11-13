package com.example.cardiotempo;

import static java.lang.Math.max;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int maxVolume;
    private int currentVolume;
    private Socket socket;
    private PrintWriter outputWriter;
    private BufferedReader inputReader;
    private static final String SERVER_IP = "100.69.247.101"; // Adresse IP du serveur
    private static final int SERVER_PORT = 5000;
    private boolean isAutoMode = false;

    private static final float INITIAL_VOLUME_PERCENTAGE = 0.3f;
    private static final float CLIENT_MAX_VOLUME_PERCENTAGE = 0.6f; // 60% du volume max
    private int clientMaxVolume;

    public MusicThread musicThread;

    public class MusicThread extends Thread {
        private MediaPlayer mediaPlayer;
        private boolean isMusicPlayerInitialized = false; // Flag pour vérifier si MediaPlayer est prêt

        @Override
        public void run() {
            try {
                // Créez et configurez le lecteur de musique
                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.jetplane); // Changez le nom de votre fichier audio
                mediaPlayer.setLooping(true); // Boucle la musique
                mediaPlayer.start(); // Commence à jouer la musique

                isMusicPlayerInitialized = true; // Marquer comme initialisé une fois que la musique commence

                // Reste actif tant que la musique est en lecture
                while (mediaPlayer.isPlaying()) {
                    Thread.sleep(1000); // Attend une seconde avant de vérifier à nouveau
                }

            } catch (InterruptedException e) {
                Log.e("MusicThread", "Erreur dans la lecture de la musique", e);
            } finally {
                if (mediaPlayer != null) {
                    mediaPlayer.release(); // Libérer le MediaPlayer quand terminé
                }
            }
        }

        public boolean isMusicPlayerInitialized() {
            return isMusicPlayerInitialized;
        }

        public void setPlayerVolume(int systemVolume, int customMaxVolume) {
            if (isMusicPlayerInitialized) {
                float volume = (float) systemVolume / customMaxVolume;
                mediaPlayer.setVolume(volume, volume);
            }
        }

        public void stopMusic() {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation de l'AudioManager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Vérifiez si l'audioManager est correctement initialisé
        if (audioManager == null) {
            Log.e("MainActivity", "Erreur : audioManager n'a pas pu être initialisé.");
        }

        musicThread = new MusicThread();
        musicThread.start();

        // Attendre que le MusicThread initialisé le MediaPlayer
        while (!musicThread.isMusicPlayerInitialized()) {
            try {
                Thread.sleep(100); // Attendre un peu que le MediaPlayer soit prêt
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        clientMaxVolume = (int) (maxVolume * CLIENT_MAX_VOLUME_PERCENTAGE); // 60% du volume max
        currentVolume = (int) (clientMaxVolume * INITIAL_VOLUME_PERCENTAGE);
        setPlayerVolume(currentVolume, clientMaxVolume);

        Button increaseVolumeButton = findViewById(R.id.increaseVolumeButton);
        increaseVolumeButton.setOnClickListener(v -> increaseVolume(clientMaxVolume));

        Button decreaseVolumeButton = findViewById(R.id.decreaseVolumeButton);
        decreaseVolumeButton.setOnClickListener(v -> decreaseVolume());

        Button autoModeButton = findViewById(R.id.autoModeButton);
        autoModeButton.setOnClickListener(v -> {
            isAutoMode = !isAutoMode;
            if (isAutoMode) {
                sendToServer("AUTO_MODE_ON");
            } else {
                sendToServer("AUTO_MODE_OFF");
            }
        });

        // Démarrer la connexion socket
        new InitializeSocketTask().execute();
    }

    // Tâche pour initialiser la connexion socket et démarrer les threads de communication
    private class InitializeSocketTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                System.out.println("Connecté au serveur à " + SERVER_IP + ":" + SERVER_PORT);

                outputWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Erreur lors de la connexion au serveur : " + e.getMessage());
            }
            return null;
        }

        protected void onPostExecute(Void result){
            // Démarrer le thread de réception
            new ReceiveMessageTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    // Thread pour recevoir des messages depuis le serveur
    private class ReceiveMessageTask extends AsyncTask<Void, String, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            try {
                String message;
                while ((message = inputReader.readLine()) != null) {
                    publishProgress(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Erreur lors de la réception du message : " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... messages) {
            String message = messages[0];
            System.out.println("Message reçu du serveur : " + message);

            if (message.contains("Volume envoyé au client:")) {
                try {
                    String[] parts = message.split(":");
                    int newVolume = Integer.parseInt(parts[1].trim());
                    updateVolumeFromServer(newVolume);
                } catch (NumberFormatException e) {
                    System.out.println("Erreur de format du volume reçu : " + message);
                }
            } else {
                System.out.println("Message non reconnu : " + message);
            }
        }
    }

    // Fonction pour envoyer des messages au serveur
    private void sendToServer(String message) {
        new SendMessageTask().execute(message);
    }

    private class SendMessageTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... messages) {
            String message = messages[0];
            try {
                if (outputWriter != null) {
                    outputWriter.println(message);
                    outputWriter.flush();
                    System.out.println("Message envoyé au serveur : " + message);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Erreur lors de l'envoi du message : " + e.getMessage());
            }
            return null;
        }
    }

    private void updateVolumeFromServer(int receivedVolume) {
        // Normalisation du volume reçu (échelle 0-150) à l'échelle système
        int denormalizedVolume = (int) ((receivedVolume / 90.0) * clientMaxVolume);

        // Vérifiez que le volume est dans les limites acceptables
        if (denormalizedVolume > clientMaxVolume) {
            denormalizedVolume = clientMaxVolume;
        }
        if (denormalizedVolume < 0) {
            denormalizedVolume = 0;
        }

        // Appliquer le volume au système audio
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, receivedVolume*10, 0);
        currentVolume = receivedVolume*10;

        // Mettre à jour le volume du MediaPlayer
        setPlayerVolume(currentVolume, clientMaxVolume);

        System.out.println("Volume mis à jour depuis le serveur : " + currentVolume + " (dénormalisé)");
    }


    private void setPlayerVolume(int systemVolume, int customMaxVolume) {
        musicThread.setPlayerVolume(systemVolume, customMaxVolume);
    }

    private void increaseVolume(int customMaxVolume) {
        if (currentVolume < customMaxVolume) {
            currentVolume += 5;
            if (currentVolume > customMaxVolume) {
                currentVolume = customMaxVolume; // Limiter à 60% du volume max
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, customMaxVolume);

            // Assurez-vous que le message est envoyé au serveur
            sendToServer("Volume actuel brut:" + currentVolume); // Message envoyé au serveur
            System.out.println("Volume brut augmenté à : " + currentVolume);
        }
    }

    private void decreaseVolume() {
        if (currentVolume > 0) {
            currentVolume -= 5;
            currentVolume = max(currentVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, maxVolume);

            // Assurez-vous que le message est envoyé au serveur
            sendToServer("Volume actuel:" + currentVolume); // Message envoyé au serveur
            System.out.println("Volume diminué à : " + currentVolume);
        }
    }


    private int normalizeVolumeForServer(int volume, int maxVolume) {
        return (int) ((volume / (float) maxVolume) * clientMaxVolume); // Échelle 0-90 pour normaliser
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
