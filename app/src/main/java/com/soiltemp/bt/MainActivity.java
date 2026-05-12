package com.soiltemp.bt;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_BT_CONNECT = 1001;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private Thread readerThread;
    private volatile boolean readerRunning = false;
    private volatile boolean csvEnabled = true;

    private Spinner deviceSpinner;
    private TextView tempText;
    private TextView statusText;
    private TextView lastUpdateText;
    private TextView connectionText;
    private TextView logText;
    private CheckBox csvCheck;
    private EditText intervalEdit;
    private EditText calibrationEdit;
    private EditText awakeEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildInterface();
        requestBtPermissionIfNeeded();
        loadPairedDevices();
    }

    private void buildInterface() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(Color.rgb(232, 244, 255));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("SoilTemp BT Monitor");
        title.setGravity(Gravity.CENTER);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(16, 57, 92));
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("ATmega328P + DS18B20 + HC-05 / BT-05 Classic");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(80, 98, 115));
        root.addView(subtitle, matchWrapMargins(0, 2, 0, 12));

        tempText = card("--.- °C", 44, Color.rgb(0, 105, 160), true);
        root.addView(tempText, matchWrapMargins(0, 4, 0, 8));

        statusText = card("Status senzor: astept date", 16, Color.rgb(30, 50, 70), false);
        root.addView(statusText, matchWrapMargins(0, 0, 0, 8));

        connectionText = small("Conexiune: neconectat");
        root.addView(connectionText, matchWrapMargins(0, 4, 0, 0));

        lastUpdateText = small("Ultima actualizare: --:--:--");
        root.addView(lastUpdateText, matchWrapMargins(0, 2, 0, 10));

        TextView pickLabel = small("Dispozitiv Bluetooth imperecheat:");
        root.addView(pickLabel, matchWrapMargins(0, 10, 0, 2));

        deviceSpinner = new Spinner(this);
        root.addView(deviceSpinner, matchWrapMargins(0, 0, 0, 6));

        LinearLayout rowConnect = row();
        Button refreshBtn = button("Reincarca lista");
        Button connectBtn = button("Conectare");
        Button disconnectBtn = button("Deconectare");
        rowConnect.addView(refreshBtn, weight());
        rowConnect.addView(connectBtn, weight());
        rowConnect.addView(disconnectBtn, weight());
        root.addView(rowConnect, matchWrapMargins(0, 4, 0, 0));

        LinearLayout rowCommands = row();
        Button readBtn = button("Citire acum");
        Button autoOnBtn = button("AUTO ON");
        Button autoOffBtn = button("AUTO OFF");
        rowCommands.addView(readBtn, weight());
        rowCommands.addView(autoOnBtn, weight());
        rowCommands.addView(autoOffBtn, weight());
        root.addView(rowCommands, matchWrapMargins(0, 8, 0, 0));

        LinearLayout rowInterval = row();
        intervalEdit = edit("10");
        Button intervalBtn = button("Set interval secunde");
        rowInterval.addView(intervalEdit, weight());
        rowInterval.addView(intervalBtn, weight2());
        root.addView(rowInterval, matchWrapMargins(0, 8, 0, 0));

        LinearLayout rowCal = row();
        calibrationEdit = edit("0.00");
        Button calBtn = button("Set calibrare °C");
        rowCal.addView(calibrationEdit, weight());
        rowCal.addView(calBtn, weight2());
        root.addView(rowCal, matchWrapMargins(0, 8, 0, 0));

        LinearLayout rowAwake = row();
        awakeEdit = edit("180");
        Button awakeBtn = button("AWAKE secunde");
        rowAwake.addView(awakeEdit, weight());
        rowAwake.addView(awakeBtn, weight2());
        root.addView(rowAwake, matchWrapMargins(0, 8, 0, 0));

        LinearLayout rowPower = row();
        Button sleepBtn = button("SLEEP");
        Button pingBtn = button("PING");
        Button infoBtn = button("INFO");
        rowPower.addView(sleepBtn, weight());
        rowPower.addView(pingBtn, weight());
        rowPower.addView(infoBtn, weight());
        root.addView(rowPower, matchWrapMargins(0, 8, 0, 0));

        csvCheck = new CheckBox(this);
        csvCheck.setText("Salvare CSV activa");
        csvCheck.setTextSize(16);
        csvCheck.setChecked(true);
        csvCheck.setTextColor(Color.rgb(30, 50, 70));
        csvCheck.setOnCheckedChangeListener((buttonView, isChecked) -> csvEnabled = isChecked);
        root.addView(csvCheck, matchWrapMargins(0, 10, 0, 0));

        LinearLayout rowLog = row();
        Button clearLogBtn = button("Sterge jurnal");
        Button clearCsvBtn = button("Sterge CSV");
        rowLog.addView(clearLogBtn, weight());
        rowLog.addView(clearCsvBtn, weight());
        root.addView(rowLog, matchWrapMargins(0, 8, 0, 0));

        TextView logLabel = small("Jurnal comunicatie:");
        root.addView(logLabel, matchWrapMargins(0, 12, 0, 2));

        logText = new TextView(this);
        logText.setTextSize(12);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextColor(Color.rgb(20, 20, 20));
        logText.setBackgroundColor(Color.WHITE);
        logText.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(logText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        setContentView(scrollView);

        refreshBtn.setOnClickListener(v -> loadPairedDevices());
        connectBtn.setOnClickListener(v -> connectSelectedDevice());
        disconnectBtn.setOnClickListener(v -> disconnect());
        readBtn.setOnClickListener(v -> sendCommand("READ"));
        autoOnBtn.setOnClickListener(v -> sendCommand("AUTO=1"));
        autoOffBtn.setOnClickListener(v -> sendCommand("AUTO=0"));
        intervalBtn.setOnClickListener(v -> sendCommand("INT=" + intervalEdit.getText().toString().trim()));
        calBtn.setOnClickListener(v -> sendCommand("CAL=" + calibrationEdit.getText().toString().trim()));
        awakeBtn.setOnClickListener(v -> sendCommand("AWAKE=" + awakeEdit.getText().toString().trim()));
        sleepBtn.setOnClickListener(v -> sendCommand("SLEEP"));
        pingBtn.setOnClickListener(v -> sendCommand("PING"));
        infoBtn.setOnClickListener(v -> sendCommand("INFO"));
        clearLogBtn.setOnClickListener(v -> logText.setText(""));
        clearCsvBtn.setOnClickListener(v -> clearCsvFile());
    }

    private TextView card(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(12), dp(18), dp(12), dp(18));
        t.setBackgroundColor(Color.WHITE);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView small(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Color.rgb(60, 76, 90));
        return t;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private EditText edit(String text) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setSingleLine(true);
        e.setGravity(Gravity.CENTER);
        return e;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(l), dp(t), dp(r), dp(b));
        return params;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams weight2() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void requestBtPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT_CONNECT);
        }
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadPairedDevices() {
        if (bluetoothAdapter == null) {
            toast("Telefonul nu are Bluetooth.");
            return;
        }
        if (!hasBtPermission()) {
            requestBtPermissionIfNeeded();
            return;
        }

        pairedDevices.clear();
        List<String> labels = new ArrayList<>();
        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bonded) {
                pairedDevices.add(device);
                labels.add(safeDeviceName(device) + "  |  " + device.getAddress());
            }
        } catch (SecurityException e) {
            toast("Permisiune Bluetooth lipsa.");
        }

        if (labels.isEmpty()) {
            labels.add("Niciun dispozitiv. Imperecheaza HC-05/BT-05 din setarile Android.");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        deviceSpinner.setAdapter(adapter);
        appendLog("Lista Bluetooth reincarcata: " + pairedDevices.size() + " dispozitiv(e).");
    }

    private void connectSelectedDevice() {
        if (bluetoothAdapter == null) {
            toast("Telefonul nu are Bluetooth.");
            return;
        }
        if (!hasBtPermission()) {
            requestBtPermissionIfNeeded();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            toast("Porneste Bluetooth din setarile telefonului.");
            return;
        }

        int pos = deviceSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= pairedDevices.size()) {
            toast("Alege HC-05 / BT-05 din lista.");
            return;
        }

        BluetoothDevice device = pairedDevices.get(pos);
        appendLog("Conectare la " + safeDeviceName(device) + "...");
        connectionText.setText("Conexiune: conectare...");

        new Thread(() -> {
            try {
                closeSocketOnly();
                if (hasBtPermission()) {
                    bluetoothAdapter.cancelDiscovery();
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    socket.connect();
                    outputStream = socket.getOutputStream();
                    readerRunning = true;
                    runOnUiThread(() -> connectionText.setText("Conexiune: conectat la " + safeDeviceName(device)));
                    appendLog("Conectat cu succes.");
                    startReaderThread();
                    sendCommand("INFO");
                }
            } catch (Exception e) {
                appendLog("Eroare conectare: " + cleanError(e));
                runOnUiThread(() -> connectionText.setText("Conexiune: eroare"));
                closeSocketOnly();
            }
        }).start();
    }

    private void startReaderThread() {
        readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while (readerRunning && (line = reader.readLine()) != null) {
                    handleReceivedLine(line.trim());
                }
            } catch (Exception e) {
                if (readerRunning) appendLog("Citire oprita: " + cleanError(e));
            }
        });
        readerThread.start();
    }

    private void handleReceivedLine(String line) {
        if (line == null || line.length() == 0) return;
        appendLog("RX: " + line);

        if (line.startsWith("TEMP=")) {
            String temp = getField(line, "TEMP");
            String status = getField(line, "STATUS");
            if (status.length() == 0) status = "OK";
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String finalStatus = status;
            String finalTemp = temp;

            runOnUiThread(() -> {
                if ("ERR".equals(finalTemp)) {
                    tempText.setText("EROARE");
                    tempText.setTextColor(Color.rgb(180, 55, 45));
                } else {
                    tempText.setText(finalTemp + " °C");
                    tempText.setTextColor(Color.rgb(0, 105, 160));
                }
                statusText.setText("Status senzor: " + finalStatus);
                lastUpdateText.setText("Ultima actualizare: " + time);
            });

            if (csvEnabled) {
                saveCsv(finalTemp, finalStatus, line);
            }
        }
    }

    private String getField(String msg, String key) {
        String[] parts = msg.split(";");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                String name = part.substring(0, idx).trim();
                if (key.equals(name)) return part.substring(idx + 1).trim();
            }
        }
        return "";
    }

    private void sendCommand(String command) {
        new Thread(() -> {
            try {
                if (outputStream == null) {
                    appendLog("Nu este conectat. Comanda nu a fost trimisa: " + command);
                    return;
                }
                outputStream.write((command + "\n").getBytes("UTF-8"));
                outputStream.flush();
                appendLog("TX: " + command);
            } catch (Exception e) {
                appendLog("Eroare trimitere: " + cleanError(e));
            }
        }).start();
    }

    private void saveCsv(String temp, String status, String raw) {
        try {
            File file = new File(getExternalFilesDir(null), "soil_temperature.csv");
            boolean newFile = !file.exists();
            FileWriter writer = new FileWriter(file, true);
            if (newFile) {
                writer.write("Data,Temperatura_C,Ora,Status,Mesaj_raw\n");
            }
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            String cleanRaw = raw.replace("\"", "'");
            writer.write(date + "," + temp + "," + time + "," + status + ",\"" + cleanRaw + "\"\n");
            writer.close();
        } catch (Exception e) {
            appendLog("CSV error: " + cleanError(e));
        }
    }

    private void clearCsvFile() {
        try {
            File file = new File(getExternalFilesDir(null), "soil_temperature.csv");
            if (file.exists()) {
                if (file.delete()) appendLog("CSV sters.");
                else appendLog("CSV nu poate fi sters.");
            } else {
                appendLog("CSV nu exista inca.");
            }
        } catch (Exception e) {
            appendLog("Eroare stergere CSV: " + cleanError(e));
        }
    }

    private void disconnect() {
        readerRunning = false;
        closeSocketOnly();
        connectionText.setText("Conexiune: deconectat");
        appendLog("Deconectat.");
    }

    private void closeSocketOnly() {
        try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        outputStream = null;
        socket = null;
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            if (name == null || name.length() == 0) return device.getAddress();
            return name;
        } catch (SecurityException e) {
            return "Bluetooth device";
        }
    }

    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        runOnUiThread(() -> {
            String old = logText.getText().toString();
            String next = old + "[" + time + "] " + message + "\n";
            if (next.length() > 12000) next = next.substring(next.length() - 10000);
            logText.setText(next);
        });
    }

    private String cleanError(Exception e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    @Override
    protected void onDestroy() {
        readerRunning = false;
        closeSocketOnly();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT_CONNECT) loadPairedDevices();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
