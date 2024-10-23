package com.example.cardiotempo;

import androidx.appcompat.app.AppCompatActivity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int maxVolume;
    private int currentVolume;

    // Constantes pour définir un volume de départ et un volume maximum personnalisé
    private static final float INITIAL_VOLUME_PERCENTAGE = 0.3f; // 30% du max
    private static final float CUSTOM_MAX_VOLUME_PERCENTAGE = 0.8f; // Limiter à 80% du volume max système

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation du lecteur multimédia avec un fichier MP3
        mediaPlayer = MediaPlayer.create(this, R.raw.jetplane);

        // Gestion du son avec AudioManager
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        // Récupération du volume maximal du système
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        // Définir le volume maximal personnalisé comme une proportion du volume maximal du système
        int customMaxVolume = (int) (maxVolume * CUSTOM_MAX_VOLUME_PERCENTAGE);

        // Initialiser le volume à 70% du volume maximum personnalisé
        currentVolume = (int) (customMaxVolume * INITIAL_VOLUME_PERCENTAGE);
        setPlayerVolume(currentVolume, customMaxVolume);

        // Lancer la lecture du fichier audio
        mediaPlayer.start();

        // Bouton pour augmenter le volume
        Button increaseVolumeButton = findViewById(R.id.increaseVolumeButton);
        increaseVolumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                increaseVolume(customMaxVolume);
            }
        });

        // Bouton pour diminuer le volume
        Button decreaseVolumeButton = findViewById(R.id.decreaseVolumeButton);
        decreaseVolumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decreaseVolume();
            }
        });
    }

    // Méthode pour ajuster le volume du MediaPlayer avec le volume maximal personnalisé
    private void setPlayerVolume(int systemVolume, int customMaxVolume) {
        float volume = (float) systemVolume / customMaxVolume;
        mediaPlayer.setVolume(volume, volume);
    }

    // Méthode pour augmenter le volume, limitée au volume maximal personnalisé
    private void increaseVolume(int customMaxVolume) {
        if (currentVolume < customMaxVolume) {
            currentVolume++;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
            setPlayerVolume(currentVolume, customMaxVolume);
        }
    }

    // Méthode pour diminuer le volume
    private void decreaseVolume() {
        if (currentVolume > 0) {
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
    }
}
