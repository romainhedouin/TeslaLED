package com.teslaowls.teslaled;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.LinkedHashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH_CONNECT = 1;
    // A dictionary with the features and their corresponding commands, in this order
    private static final Map<String, PanelCommand> featuresToCommands = new LinkedHashMap<String, PanelCommand>() {{
        put("Bonjour", new ImageCommand("bonjour.ppm", 3000));
        put("Hello", new ImageCommand("hello.ppm", 3000));
        put("Merci", new ImageCommand("merci.ppm", 3000));
        put("Thanks", new ImageCommand("thanks.ppm", 3000));
        put("Desole", new ImageCommand("desole.ppm", 3000));
        put("Sorry", new ImageCommand("sorry.ppm", 3000));
        put("Tesla", new ImageCommand("tesla.ppm", 2000));
        put("Tesla ❤️", new LegacyCommand("tesla", 5000));
        put("Feux de croisement", new ImageCommand("feu-croisement.ppm", 4000));
        put("Clignotants", new ImageCommand("clignotants.ppm", 4000));
        put("Distance", new ImageCommand("distance.ppm", 6000));
        put("❤️", new ImageCommand("red_heart.ppm", 2000));
        //put("Carre", new LegacyCommand("carre", 5000));
    }};

    BluetoothClient bluetoothClient = new BluetoothClient(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
        }

        GridLayout gridLayout = findViewById(R.id.command_grid);
        for (final String feature : featuresToCommands.keySet()) {
            Button button = new Button(this);
            // make the button fill the cell
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.height = 250;
            params.width = 0;
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            button.setLayoutParams(params);
            button.setText(feature);
            button.setTextSize(26);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PanelCommand currentCommand = featuresToCommands.get(feature);
                    if (currentCommand != null) {
                        boolean isSendCommandSuccess = initiateCommand(currentCommand);
                        buttonColorFeedback(button, isSendCommandSuccess, currentCommand);
                    } else {
                        System.out.println("[-] Current command not found.");
                        buttonColorFeedback(button, false, currentCommand);
                    }
                }
            });
            gridLayout.addView(button);
        }
    }

    private void buttonColorFeedback(Button button, boolean isSendCommandSuccess, PanelCommand currentCommand) {
        int temporaryColor;
        if (!isSendCommandSuccess) {
            temporaryColor = 0xFFFF0000;
            Toast.makeText(MainActivity.this, "Unknown error while sending message.", Toast.LENGTH_SHORT).show();
            System.out.println("[-] Failed to initiate command.");
        } else {
            temporaryColor = 0xFF00FF00;
        }
        final Drawable originalColor = button.getBackground();
        int sleepDurationMs = (currentCommand == null ? 2000 : currentCommand.getSleepDurationMs());
        button.setBackgroundColor(temporaryColor);
        button.postDelayed(new Runnable() {
            @Override
            public void run() {
                button.setBackground(originalColor);
                if (currentCommand != null) {
                    currentCommand.kill(bluetoothClient);
                }
            }
        }, sleepDurationMs);
    }

    private boolean initiateCommand(PanelCommand command) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission not granted.", Toast.LENGTH_SHORT).show();
            return false;
        }
        bluetoothClient.findDevice();
        if (!bluetoothClient.isConnected()) {
            bluetoothClient.connect();
        }
        if (bluetoothClient.isConnected()) {
            return command.sendCommand(bluetoothClient, this.getAssets());
        }
        System.out.println("[-] Failed to send message.");
        return false;
    }
}